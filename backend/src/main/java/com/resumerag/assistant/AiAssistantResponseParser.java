package com.resumerag.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AiSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Parses the assistant's JSON reply.
 *
 * <p>Two things happen here that matter for trust:
 * <ul>
 *   <li>The model's own {@code sources} array is intersected with the context we
 *       actually supplied. A model that claims to have used a section it was
 *       never shown would otherwise produce a citation the user cannot verify -
 *       and cannot exist in the first place.</li>
 *   <li>Follow-up questions are restricted to the catalogue, so a follow-up can
 *       never smuggle in a fresh instruction.</li>
 * </ul>
 */
@Component
public class AiAssistantResponseParser {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantResponseParser.class);

    /** Shown when the model produced no usable prose. */
    static final String INSUFFICIENT_CONTEXT_ANSWER =
            "I don't see enough information in your uploaded resume or job description to answer that "
                    + "confidently. You can ask me about the information available in your analysis, or "
                    + "upload/update the relevant document.";

    /** Shown when the reply could not be understood at all. */
    static final String UNREADABLE_REPLY_ANSWER =
            "I wasn't able to put together a clear answer for that. Please try rephrasing your "
                    + "question, or pick one of the suggested questions.";

    private static final int MAX_FOLLOW_UP_CHARS = 120;

    /** Upper bound on prose we are willing to show when the model ignored the JSON contract. */
    private static final int MAX_PROSE_CHARS = 4000;

    private final ObjectMapper objectMapper;
    private final QuestionCatalog catalog;

    public AiAssistantResponseParser(ObjectMapper objectMapper, QuestionCatalog catalog) {
        this.objectMapper = objectMapper;
        this.catalog = catalog;
    }

    public record Parsed(String answer, List<String> suggestedFollowUps, List<AiSource> sources) {}

    /**
     * Turns one model reply into an answer, and never throws.
     *
     * <p>A local model does not reliably honour a JSON contract: it can stop
     * mid-object once it hits an output limit, or ignore the contract entirely
     * and answer in prose. Letting either case throw meant the whole turn
     * surfaced as a 500 with no answer at all, which is the one failure mode
     * that makes the whole feature look broken. Instead the reply is repaired
     * where that is unambiguous, degraded to plain prose when it is not, and
     * only reported as unreadable when there is genuinely nothing to show.
     *
     * <p>Sources and follow-ups are dropped on the prose path: they cannot be
     * attributed to the structure we asked for, so we will not guess at them.
     */
    public Parsed parse(String content, Set<String> allowedSourceKeys) {
        JsonNode root = readJson(content);

        if (root == null) {
            String prose = asProse(content);
            if (!prose.isBlank()) {
                log.warn("Assistant replied without the expected JSON structure; showing it as prose");
                return new Parsed(prose, List.of(), List.of());
            }
            log.warn("Assistant reply could not be read at all");
            return new Parsed(UNREADABLE_REPLY_ANSWER, List.of(), List.of());
        }

        String answer = text(root, "answer");
        if (answer.isBlank()) {
            // A structurally valid reply with no prose is the same failure as
            // malformed output: we have nothing to show the user.
            log.warn("Assistant reply contained no answer text");
            return new Parsed(INSUFFICIENT_CONTEXT_ANSWER, List.of(), List.of());
        }

        List<String> followUps = followUps(root);
        List<AiSource> sources = sources(root, allowedSourceKeys);
        return new Parsed(answer, followUps, sources);
    }

    /**
     * The model's own words when it skipped the JSON contract.
     *
     * <p>Still grounded - it came from the same prompt with the same evidence -
     * so it is shown rather than discarded. The shape check matters as much as
     * the cap: showing {@code }{ nonsense [} verbatim because it failed to
     * parse would replace one bad message with a worse one.
     */
    private String asProse(String content) {
        if (content == null || content.isBlank()) {
            return "";
        }
        String text = stripFences(content).trim();
        if (!looksLikeProse(text)) {
            return "";
        }
        return text.length() > MAX_PROSE_CHARS ? text.substring(0, MAX_PROSE_CHARS) + "..." : text;
    }

    /**
     * Cheap guard against presenting structural debris as an answer: a reply that
     * opens with JSON punctuation, or is mostly punctuation, is not prose the
     * user should read.
     */
    private boolean looksLikeProse(String text) {
        if (text.isEmpty() || "{}[],:".indexOf(text.charAt(0)) >= 0) {
            return false;
        }
        int lettersAndSpaces = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isLetterOrDigit(ch) || Character.isWhitespace(ch)) {
                lettersAndSpaces++;
            }
        }
        return text.isEmpty() || (double) lettersAndSpaces / text.length() >= 0.6;
    }

    /**
     * Reads the reply as JSON, repairing the common truncation shapes.
     *
     * <p>Returns null rather than throwing when the reply cannot be understood,
     * so the caller decides how to degrade.
     */
    private JsonNode readJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        String candidate = stripFences(content);

        JsonNode direct = tryRead(candidate);
        if (direct != null) {
            return direct;
        }

        // An object wrapped in commentary: "here you go: {...}". Taking the
        // outermost braces is right more often than not, and every attempt is
        // still parse-checked before use.
        int start = candidate.indexOf('{');
        int end = candidate.lastIndexOf('}');
        if (start >= 0 && end > start) {
            JsonNode embedded = tryRead(candidate.substring(start, end + 1));
            if (embedded != null) {
                return embedded;
            }
        }

        for (String repaired : repairCandidates(candidate)) {
            JsonNode node = tryRead(repaired);
            if (node != null) {
                log.warn("Repaired a truncated assistant reply");
                return node;
            }
        }
        return null;
    }

    private JsonNode tryRead(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Candidate repairs for a reply the model cut off mid-generation.
     *
     * <p>Each one is closed back up and parse-checked by the caller, so a wrong
     * guess costs nothing - which is what makes it safe to try several:
     * <ol>
     *   <li>Close the open string and containers, e.g. a reply that stopped
     *       partway through the {@code answer} value.</li>
     *   <li>Drop a trailing partial key and close, e.g. it reached
     *       {@code ..., "sources"} and stopped.</li>
     *   <li>Drop a trailing partial key and give it an empty value, e.g. it
     *       reached {@code ..., "answer":} and stopped.</li>
     * </ol>
     */
    private List<String> repairCandidates(String raw) {
        List<String> candidates = new ArrayList<>();
        String closed = closeStructure(raw);
        candidates.add(closed);

        String withoutKey = trimToLastSeparator(raw);
        if (!withoutKey.isBlank()) {
            candidates.add(closeStructure(withoutKey));
            // `"answer":` with nothing after it needs a value to be valid JSON.
            candidates.add(closeStructure(withoutKey) + "\"\"");
        }
        return candidates;
    }

    /** Drops everything after the last {@code ,} or {@code :}, i.e. a partial key. */
    private String trimToLastSeparator(String raw) {
        int comma = raw.lastIndexOf(',');
        int colon = raw.lastIndexOf(':');
        int cut = Math.max(comma, colon);
        return cut > 0 ? raw.substring(0, cut) : "";
    }

    /**
     * Closes whatever JSON structure is still open, in the right order.
     *
     * <p>Tracks string state so a brace inside the answer text is not mistaken
     * for structure - an unterminated string is closed first, then the open
     * containers are closed innermost-last.
     */
    private String closeStructure(String raw) {
        StringBuilder open = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        // Reverse order of opening, so popping yields the matching closers.
        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();

        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (ch == '\\') {
                    escaped = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            switch (ch) {
                case '"' -> inString = true;
                case '{' -> stack.push('{');
                case '[' -> stack.push('[');
                case '}' -> pop(stack);
                case ']' -> pop(stack);
                default -> { }
            }
        }

        StringBuilder sb = new StringBuilder(raw);
        if (inString) {
            sb.append('"');
        }
        while (!stack.isEmpty()) {
            char opener = stack.pop();
            sb.append(opener == '{' ? '}' : ']');
        }
        return sb.toString();
    }

    private void pop(java.util.Deque<Character> stack) {
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    private String stripFences(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int closing = trimmed.lastIndexOf("```");
        if (firstNewline > 0 && closing > firstNewline) {
            return trimmed.substring(firstNewline + 1, closing).trim();
        }
        return trimmed.replace("```json", "").replace("```", "").trim();
    }

    private List<String> followUps(JsonNode root) {
        List<String> catalogueFirst = new ArrayList<>();
        List<String> novel = new ArrayList<>();
        JsonNode node = root.get("suggestedFollowUps");
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                if (!item.isTextual()) {
                    continue;
                }
                String value = item.asText().trim();
                if (value.isEmpty() || value.length() > MAX_FOLLOW_UP_CHARS || !looksLikeAQuestion(value)) {
                    continue;
                }
                // A follow-up is a suggestion shown to the user and clicked, not
                // an instruction to the model, so arbitrary phrasing is fine -
                // the user could always have typed it. Catalogue questions are
                // ordered first because they are the ones we know we can ground.
                if (catalog.findCategory(value) != null) {
                    if (!catalogueFirst.contains(value)) {
                        catalogueFirst.add(value);
                    }
                } else if (!novel.contains(value)) {
                    novel.add(value);
                }
            }
        }
        List<String> ordered = new ArrayList<>(catalogueFirst);
        ordered.addAll(novel);
        return ordered.stream().limit(3).toList();
    }

    private boolean looksLikeAQuestion(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        if (lower.endsWith("?") || lower.startsWith("what ") || lower.startsWith("which ")
                || lower.startsWith("how ") || lower.startsWith("why ") || lower.startsWith("can ")
                || lower.startsWith("does ") || lower.startsWith("is ") || lower.startsWith("are ")
                || lower.startsWith("should ") || lower.startsWith("could ")) {
            return true;
        }
        // Anything phrased as an instruction to the assistant is rejected: a
        // suggestion must read as a question the user might ask.
        return !lower.contains("ignore ") && !lower.contains("system prompt")
                && !lower.contains("you are ") && !lower.contains("instruction");
    }

    private List<AiSource> sources(JsonNode root, Set<String> allowedKeys) {
        List<AiSource> sources = new ArrayList<>();
        JsonNode node = root.get("sources");
        if (node == null || !node.isArray()) {
            return sources;
        }
        for (JsonNode item : node) {
            if (!item.isObject()) {
                continue;
            }
            String type = normaliseType(item.path("type").asText(""));
            String section = item.path("section").asText("").trim();
            if (section.isEmpty()) {
                continue;
            }
            AiSource candidate = new AiSource(type, normaliseSection(type, section));
            if (!candidate.isValidType()) {
                continue;
            }
            // The model may only cite context we actually supplied. A "<type>::*"
            // entry in the allow-list grants that whole type, and is used only
            // where we hand the model the entire document rather than named
            // excerpts: the analysis block, and the job-description passages
            // (see AiAssistantService#allowedSourceKeys).
            String key = type + "::" + section.toLowerCase(Locale.ROOT);
            boolean allowed = allowedKeys != null
                    && (allowedKeys.contains(key) || allowedKeys.contains(type + "::*"));
            if (!allowed) {
                log.warn("Dropped source the model could not have seen: {}", key);
                continue;
            }
            if (!sources.contains(candidate)) {
                sources.add(candidate);
            }
        }
        return sources.stream().limit(6).toList();
    }

    /**
     * Maps whatever the model wrote onto our canonical type names.
     *
     * <p>Needed because a case-insensitive compare would turn "jobDescription"
     * into "jobdescription", which then fails every downstream equality check
     * against the canonical constant and silently drops a legitimate citation.
     */
    private String normaliseType(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        return switch (value) {
            case "resume" -> AiSource.RESUME;
            case "jobdescription", "job_description", "job", "jd" -> AiSource.JOB_DESCRIPTION;
            case "analysis" -> AiSource.ANALYSIS;
            default -> value;
        };
    }

    /**
     * Collapses the model's free-text section labels onto the small set the UI
     * renders. A job description has no named sections in our retrieval, so
     * "Requirements", "Job description" and "Role" all become the same honest
     * label rather than three that imply precision we do not have.
     */
    private String normaliseSection(String type, String section) {
        String lower = section.toLowerCase(Locale.ROOT);
        if (AiSource.JOB_DESCRIPTION.equals(type)) {
            return lower.contains("role") || lower.contains("compan")
                    ? "Role" : "Requirements";
        }
        if (AiSource.ANALYSIS.equals(type)) {
            return "Match analysis";
        }
        // Resume sections are already lower-cased by the chunker; title-case
        // them for display without inventing a heading the resume lacks.
        String[] words = section.split("\\s+");
        StringBuilder sb = new StringBuilder(section.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Character.toUpperCase(words[i].charAt(0)))
              .append(words[i].substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        return node == null || node.isNull() ? "" : node.asText().trim();
    }
}
