package com.resumerag.generation;

import com.resumerag.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class PromptBuilder {

    private static final Logger log = LoggerFactory.getLogger(PromptBuilder.class);

    private static final int MAX_JD_CHARS = 12000;
    private static final int MAX_CHUNK_CHARS = 2000;

    /**
     * The JSON shape requested here must stay in lockstep with
     * com.resumerag.dto.AnalysisResult, which is what ResponseParser deserialises
     * into. A mismatch (e.g. asking for "overallScore"/"weaknesses" while the DTO
     * expects "matchScore"/"gaps") makes the model return a shape the parser
     * can't populate, which surfaces as a 0 score and null skill lists.
     *
     * <p>matchedSkills and missingSkills are deliberately NOT requested. They are
     * computed deterministically by the skill trie over the full resume, and
     * asking a model to restate a list it is already shown is what produced
     * analyses that reported "Terraform" as a matched skill while simultaneously
     * writing "no experience with infrastructure as code" into gaps.
     */
    public String buildUserPrompt(String jobDescriptionText,
                                  List<RetrievedChunk> retrievedChunks,
                                  Set<String> requiredSkills,
                                  Set<String> matchedSkills,
                                  Set<String> missingSkills) {
        return buildUserPrompt(jobDescriptionText, retrievedChunks, requiredSkills, matchedSkills, missingSkills, 0);
    }

    /**
     * @param maxChars soft ceiling on the whole rendered prompt. Excerpts are
     *                 dropped from the least-relevant end until the prompt fits.
     *                 Pass 0 for no limit.
     */
    public String buildUserPrompt(String jobDescriptionText,
                                  List<RetrievedChunk> retrievedChunks,
                                  Set<String> requiredSkills,
                                  Set<String> matchedSkills,
                                  Set<String> missingSkills,
                                  int maxChars) {
        String jd = truncate(jobDescriptionText, MAX_JD_CHARS);
        if (jobDescriptionText != null && jobDescriptionText.length() > MAX_JD_CHARS) {
            log.warn("Job description is {} chars; truncated to {} before prompting. "
                            + "Tail requirements are invisible to the model.",
                    jobDescriptionText.length(), MAX_JD_CHARS);
        }

        List<RetrievedChunk> excerpts = selectExcerpts(retrievedChunks, matchedSkills, requiredSkills,
                missingSkills, jd, maxChars);

        StringBuilder sb = new StringBuilder();

        sb.append("## Job Description\n\n");
        sb.append(jd).append("\n\n");

        sb.append("## Retrieved Resume Excerpts\n\n");
        if (excerpts.isEmpty()) {
            sb.append("No relevant resume excerpts were found for this role.\n\n");
        } else {
            sb.append("The excerpts below are listed from MOST to LEAST relevant, and they are ")
              .append("the complete set retrieved for this role. Ground every claim in them.\n\n");
            for (int i = 0; i < excerpts.size(); i++) {
                RetrievedChunk chunk = excerpts.get(i);
                sb.append("### Excerpt ").append(i + 1).append("\n");
                sb.append("[section: ").append(chunk.section() == null ? "unknown" : chunk.section())
                  .append(" | similarity: ").append(String.format("%.2f", chunk.similarity()))
                  .append("]\n");
                sb.append(truncate(chunk.content(), MAX_CHUNK_CHARS)).append("\n\n");
            }
        }

        sb.append("## Skill Scan Results\n\n");
        sb.append("These were computed deterministically by exact-match scanning the FULL text of ")
          .append("the resume and the job description. They are the source of truth, and they already ")
          .append("account for resume content that may not appear in the excerpts above.\n\n");

        sb.append("**Required Skills (found in the job description):** ");
        sb.append(render(requiredSkills, "None specified"));
        sb.append("\n\n");

        sb.append("**Matched Skills (present in the resume):** ");
        sb.append(render(matchedSkills, "None matched"));
        sb.append("\n\n");

        sb.append("**Missing Skills (required but not found anywhere in the resume):** ");
        sb.append(render(missingSkills, "None missing"));
        sb.append("\n\n");

        sb.append("## Task\n\n");
        sb.append("Write the candidate's fit analysis for this role.\n");
        sb.append("Use only the excerpts and the skill scan above - never infer a skill, employer, ")
          .append("date, or metric that is not stated there.\n\n");
        sb.append("CRITICAL consistency rule: a skill listed under Matched Skills IS present in the ")
          .append("resume. You must never describe a matched skill as a gap, as missing, or as ")
          .append("experience the candidate lacks. Only report a gap for a skill listed under ")
          .append("Missing Skills, or for something the excerpts show as genuinely absent.\n\n");
        sb.append("Do not invent matched or missing skill lists - they are supplied above and are ")
          .append("already correct.\n\n");
        sb.append("Respond with a single JSON object and nothing else (no markdown fences), matching ")
          .append("this schema exactly:\n");
        sb.append("{\n");
        sb.append("  \"matchScore\": <integer 0-100>,\n");
        sb.append("  \"summary\": \"<string, 2-3 sentences>\",\n");
        sb.append("  \"strengths\": [\"<string>\", ...],\n");
        sb.append("  \"gaps\": [\"<string>\", ...]\n");
        sb.append("}\n");

        return sb.toString();
    }

    /**
     * Drops least-relevant excerpts until the rendered prompt fits the context
     * window, and never drops an excerpt that is the only evidence for a skill
     * the job description actually asked for.
     *
     * <p>Silently overrunning the context window is worse than trimming: Ollama
     * evicts from the middle of the prompt, so the model answers confidently from
     * a prompt whose evidence has partly disappeared, with no error anywhere.
     */
    private List<RetrievedChunk> selectExcerpts(List<RetrievedChunk> chunks,
                                                Set<String> matchedSkills,
                                                Set<String> requiredSkills,
                                                Set<String> missingSkills,
                                                String jd,
                                                int maxChars) {
        if (chunks == null || chunks.isEmpty()) {
            return List.of();
        }
        List<RetrievedChunk> kept = new ArrayList<>(chunks);
        if (maxChars <= 0) {
            return kept;
        }

        // Reserve room for everything that is not an excerpt.
        int fixed = jd.length() + 1200;

        while (kept.size() > 1 && fixed + estimateChars(kept) > maxChars) {
            // Drop the least relevant, but only if it is not the sole evidence for
            // a required skill.
            RetrievedChunk victim = null;
            for (int i = kept.size() - 1; i >= 0; i--) {
                RetrievedChunk candidate = kept.get(i);
                if (!isSoleEvidence(candidate, kept, matchedSkills, requiredSkills, missingSkills)) {
                    victim = candidate;
                    break;
                }
            }
            if (victim == null) {
                victim = kept.get(kept.size() - 1);
            }
            kept.remove(victim);
            log.warn("Prompt exceeded the context budget; dropped the least relevant excerpt "
                    + "(section={}, similarity={}) to fit. {} excerpt(s) remain.",
                    victim.section(), victim.similarity(), kept.size());
        }
        return kept;
    }

    private boolean isSoleEvidence(RetrievedChunk candidate,
                                   List<RetrievedChunk> all,
                                   Set<String> matchedSkills,
                                   Set<String> requiredSkills,
                                   Set<String> missingSkills) {
        if (matchedSkills == null || matchedSkills.isEmpty() || candidate.matchedSkills() == null) {
            return false;
        }
        Set<String> required = requiredSkills == null ? Set.of() : requiredSkills;
        for (String skill : candidate.matchedSkills()) {
            if (!required.contains(skill) || matchedSkills.contains(skill)) {
                continue;
            }
            boolean elsewhere = all.stream()
                    .anyMatch(c -> c != candidate && c.matchedSkills() != null && c.matchedSkills().contains(skill));
            if (!elsewhere) {
                return true;
            }
        }
        return false;
    }

    /**
     * Builds the narrative-only prompt: summary, strengths and gaps, and no score.
     *
     * <p>The scoring engine is deterministic and has already decided the numbers
     * by the time this is called, so the model is not asked for a score at all.
     * It previously returned {@code matchScore} and that value went straight into
     * the database, which made an unauditable number the headline of every report
     * - and free to contradict the skill scan printed in the same prompt.
     *
     * <p>Instead it is given the classified requirements and their match states,
     * and told to write prose about them. The score is not in the prompt because
     * the model must not restate it: a model that echoes a number is a model that
     * can invent one.
     */
    public String buildNarrativePrompt(String jobDescriptionText,
                                       List<RetrievedChunk> retrievedChunks,
                                       List<RequirementSummary> requirements,
                                       int maxChars) {
        String jd = truncate(jobDescriptionText, MAX_JD_CHARS);
        List<RetrievedChunk> excerpts = selectNarrativeExcerpts(retrievedChunks, maxChars);

        StringBuilder sb = new StringBuilder();
        sb.append("## Job Description\n\n").append(jd).append("\n\n");

        sb.append("## Retrieved Resume Excerpts\n\n");
        if (excerpts.isEmpty()) {
            sb.append("No relevant resume excerpts were found.\n\n");
        } else {
            sb.append("The most relevant excerpts from the candidate's resume, most to least relevant.\n\n");
            for (int i = 0; i < excerpts.size(); i++) {
                RetrievedChunk chunk = excerpts.get(i);
                sb.append("### Excerpt ").append(i + 1).append("\n");
                sb.append("[section: ").append(chunk.section() == null ? "unknown" : chunk.section()).append("]\n");
                sb.append(truncate(chunk.content(), MAX_CHUNK_CHARS)).append("\n\n");
            }
        }

        sb.append("## Match Results (already computed - do not recompute)\n\n");
        sb.append("Each requirement below has already been compared with the resume by a deterministic ")
          .append("engine. These results are the source of truth.\n\n");
        for (RequirementSummary requirement : requirements) {
            sb.append("- ").append(requirement.name())
              .append(" [").append(requirement.category()).append(", ")
              .append(requirement.status());
            if (!requirement.resumeEvidence().isEmpty()) {
                sb.append(", evidence: \"")
                  .append(truncate(requirement.resumeEvidence().get(0), 240)).append("\"");
            }
            sb.append("]\n");
        }

        sb.append("\n## Task\n\n");
        sb.append("Write the candidate's fit summary for this role, in the candidate's favour where the ")
          .append("evidence supports it.\n\n");
        sb.append("Rules:\n");
        sb.append("- The score has already been calculated. Do not state, estimate or imply a number.\n");
        sb.append("- A requirement marked NOT_EXPLICITLY_MENTIONED means the resume is silent about it. ")
          .append("Describe it as \"not explicitly mentioned in the resume\". Never say the candidate ")
          .append("lacks, cannot do, or is unable to do it - a resume is a document, and its silences are ")
          .append("evidence about the document.\n");
        sb.append("- Only list a strength for a requirement marked EXPLICIT_MATCH or ")
          .append("STRONG_CONTEXTUAL_MATCH.\n");
        sb.append("- Only list a gap for a requirement marked NOT_EXPLICITLY_MENTIONED, and phrase it ")
          .append("the same way.\n");
        sb.append("- Never invent a skill, employer, date or metric.\n\n");
        sb.append("Respond with a single JSON object and nothing else (no markdown fences):\n");
        sb.append("{\n");
        sb.append("  \"summary\": \"<string, 2-3 sentences>\",\n");
        sb.append("  \"strengths\": [\"<string>\", ...],\n");
        sb.append("  \"gaps\": [\"<string>\", ...]\n");
        sb.append("}\n");
        return sb.toString();
    }

    /**
     * Drops the least relevant excerpts until the narrative prompt fits.
     *
     * <p>Unlike the analysis prompt this drops freely: the match results above it
     * already carry the evidence each conclusion rests on, so losing an excerpt
     * costs detail rather than correctness.
     */
    private List<RetrievedChunk> selectNarrativeExcerpts(List<RetrievedChunk> chunks, int maxChars) {
        List<RetrievedChunk> kept = new ArrayList<>(chunks == null ? List.of() : chunks);
        if (maxChars <= 0) {
            return kept;
        }
        while (kept.size() > 1 && estimateChars(kept) > maxChars) {
            kept.remove(kept.size() - 1);
        }
        return kept;
    }

    /**
     * One classified requirement, as handed to the narrative prompt.
     *
     * <p>A flat projection of the analysis model so the prompt builder depends on
     * four strings and a list rather than on the whole match type.
     */
    public record RequirementSummary(
            String name,
            String category,
            String status,
            List<String> resumeEvidence
    ) {
        public RequirementSummary {
            resumeEvidence = resumeEvidence == null ? List.of() : List.copyOf(resumeEvidence);
        }
    }

    private int estimateChars(List<RetrievedChunk> chunks) {
        int total = 0;
        for (RetrievedChunk chunk : chunks) {
            String content = chunk.content() == null ? "" : chunk.content();
            total += Math.min(content.length(), MAX_CHUNK_CHARS) + 120;
        }
        return total;
    }

    private String render(Set<String> skills, String fallback) {
        if (skills == null || skills.isEmpty()) {
            return fallback;
        }
        return String.join(", ", skills);
    }

    private String truncate(String text, int maxChars) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }
        return trimmed.substring(0, maxChars) + "\n... [truncated]";
    }
}
