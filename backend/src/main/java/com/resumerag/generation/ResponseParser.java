package com.resumerag.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.analysis.model.AnalysisNarrative;
import com.resumerag.dto.AnalysisResult;
import com.resumerag.exception.AnalysisFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

@Component
public class ResponseParser {

    private static final Logger log = LoggerFactory.getLogger(ResponseParser.class);

    /** Phrases that assert something is absent, used by the contradiction filter. */
    private static final List<String> ABSENCE_CUES = List.of(
            "no experience", "no evidence", "no direct", "no explicit", "no mention",
            "not demonstrated", "no hands-on", "lacks", "lacking", "lack of",
            "missing", "never", "without", "absent", "unfamiliar", "limited or no",
            "does not", "doesn't", "cannot", "can't", "isn't", "not present"
    );

    private final ObjectMapper objectMapper;

    public ResponseParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parses a narrative-only response.
     *
     * <p>Reads {@code summary}, {@code strengths} and {@code gaps}, and nothing
     * else - there is no score field to read, because the scoring engine already
     * produced the number. Falls back to {@code weaknesses} for {@code gaps},
     * which models produce interchangeably, and drops placeholder entries, which
     * otherwise reach the UI as a gap literally named "None".
     *
     * <p>A response with no summary returns empty lists rather than throwing. The
     * caller substitutes its deterministic fallback, so an unreachable or
     * half-loaded model degrades the writing quality of a report without failing
     * the analysis - which is the whole reason the score no longer comes from
     * here.
     */
    public AnalysisNarrative parseNarrative(String jsonContent) {
        JsonNode root = readJson(jsonContent);

        String summary = text(root, "summary");
        List<String> strengths = strings(root, "strengths");
        List<String> gaps = strings(root, "gaps");
        if (gaps.isEmpty()) {
            gaps = strings(root, "weaknesses");
        }
        gaps = gaps.stream().filter(g -> !isPlaceholder(g)).toList();
        strengths = strengths.stream().filter(s -> !isPlaceholder(s)).toList();

        return new AnalysisNarrative(summary, strengths, gaps);
    }

    /**
     * Parses the model's reply into a fully populated {@link AnalysisResult}.
     *
     * <p>The matched/missing skill lists passed in come from the deterministic
     * trie scan, so they deliberately override whatever the model produced: the
     * whole point of the RAG grounding is that the skill signal is auditable and
     * not hallucinated. Everything else is normalised defensively, because a
     * single missing or out-of-range field would otherwise blow up later in
     * AnalysisResponse.from (List.of(null) throws an NPE).
     */
    public AnalysisResult parse(String jsonContent,
                                Set<String> matchedSkills,
                                Set<String> missingSkills) {
        JsonNode root = readJson(jsonContent);

        int matchScore = readScore(root);
        String summary = text(root, "summary");
        if (summary.isBlank()) {
            // A placeholder here reads like a real analysis. Better to fail and
            // let the user retry than to show them an empty verdict.
            throw new AnalysisFailedException(
                    "The analysis model did not return a summary. This usually means the model "
                            + "loaded mid-request; try running the analysis again.");
        }

        List<String> strengths = strings(root, "strengths");
        List<String> gaps = strings(root, "gaps");
        if (gaps.isEmpty()) {
            gaps = strings(root, "weaknesses");
        }
        gaps = dropGapsThatContradictTheSkillScan(gaps, matchedSkills);
        gaps.removeIf(ResponseParser::isPlaceholder);

        return new AnalysisResult(
                matchScore,
                summary,
                strengths,
                gaps,
                toList(matchedSkills),
                toList(missingSkills)
        );
    }

    /**
     * A missing score used to default to 0, which is indistinguishable from a
     * genuine "0% match" - the worst possible failure for a screening tool, since
     * a model that simply forgot the field silently became a rejection.
     */
    private int readScore(JsonNode root) {
        for (String field : List.of("matchScore", "overallScore")) {
            JsonNode node = root.get(field);
            if (node != null && node.isNumber()) {
                return clampScore(node.asInt());
            }
        }
        throw new AnalysisFailedException(
                "The analysis model did not return a match score. This usually means the model "
                        + "loaded mid-request or ran out of context; try running the analysis again.");
    }

    /**
     * Removes gaps that assert the absence of a skill the deterministic scan
     * found. Small local models routinely write "no experience with Terraform"
     * into gaps while the scan - which reads the whole resume, not just the
     * retrieved excerpts - has Terraform sitting in matched skills. Showing a
     * user a gap for a skill they demonstrably have is worse than showing none.
     *
     * <p>Deliberately narrow: a gap is only dropped when it contains BOTH an
     * absence cue ("no", "lacking", ...) AND a matched skill name, so a nuanced
     * remark like "no Kubernetes at production scale" that references real skill
     * overlap is not silently discarded.
     */
    private List<String> dropGapsThatContradictTheSkillScan(List<String> gaps, Set<String> matchedSkills) {
        if (gaps.isEmpty() || matchedSkills == null || matchedSkills.isEmpty()) {
            return gaps;
        }
        List<String> kept = new ArrayList<>();
        for (String gap : gaps) {
            String lower = gap.toLowerCase();
            if (statesAbsence(lower) && mentionsAnySkill(lower, matchedSkills)) {
                log.warn("Dropped a reported gap that contradicts the deterministic skill scan: {}", gap);
                continue;
            }
            kept.add(gap);
        }
        return kept;
    }

    /**
     * Models asked for "gaps" often answer with the literal string "None" or
     * "N/A" rather than an empty array, which the UI then renders as a real gap
     * called "None".
     */
    private static boolean isPlaceholder(String value) {
        if (value == null) {
            return true;
        }
        String normalized = value.trim().toLowerCase().replace(".", "").replace("-", "").trim();
        return normalized.isEmpty()
                || normalized.equals("none")
                || normalized.equals("n/a")
                || normalized.equals("na")
                || normalized.equals("no gaps")
                || normalized.equals("none identified")
                || normalized.equals("not applicable");
    }

    private boolean statesAbsence(String lower) {
        return ABSENCE_CUES.stream().anyMatch(lower::contains);
    }

    private boolean mentionsAnySkill(String lowerText, Set<String> matchedSkills) {
        for (String skill : matchedSkills) {
            if (skill == null || skill.isBlank()) {
                continue;
            }
            // Word-boundary match so "Go" does not match "going".
            Pattern word = Pattern.compile("(?<![a-z0-9])" + Pattern.quote(skill.toLowerCase()) + "(?![a-z0-9])");
            if (word.matcher(lowerText).find()) {
                return true;
            }
        }
        return false;
    }

    private JsonNode readJson(String jsonContent) {
        if (jsonContent == null || jsonContent.isBlank()) {
            throw new AnalysisFailedException("The analysis model returned an empty response.");
        }
        String candidate = stripCodeFences(jsonContent);
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception first) {
            // Models occasionally prepend prose before the object; fall back to
            // the outermost brace-delimited span.
            int start = candidate.indexOf('{');
            int end = candidate.lastIndexOf('}');
            if (start >= 0 && end > start) {
                try {
                    return objectMapper.readTree(candidate.substring(start, end + 1));
                } catch (Exception ignored) {
                    // fall through to the error below
                }
            }
            log.error("Failed to parse analysis model response: {}", first.getMessage());
            throw new AnalysisFailedException("The analysis model returned malformed JSON: " + first.getMessage());
        }
    }

    private String stripCodeFences(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstNewline > 0 && closingFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, closingFence).trim();
        }
        return trimmed.replace("```json", "").replace("```", "").trim();
    }

    private int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText();
    }

    private List<String> strings(JsonNode root, String field) {
        List<String> values = new ArrayList<>();
        JsonNode node = root.get(field);
        if (node == null || !node.isArray()) {
            return values;
        }
        for (JsonNode item : node) {
            String value = item.isTextual() ? item.asText().trim() : item.toString().trim();
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private List<String> toList(Set<String> values) {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }
}
