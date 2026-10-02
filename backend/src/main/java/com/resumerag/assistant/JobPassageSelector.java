package com.resumerag.assistant;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Picks the job-description passages most relevant to a question.
 *
 * <p>Deliberately NOT a second vector pipeline. Job descriptions are stored as a
 * single {@code raw_text} row, never chunked or embedded - only resume chunks
 * live in pgvector. Adding an embed-and-search path for the JD would mean
 * inventing a chunking, embedding and storage scheme the analysis pipeline
 * doesn't use, purely to answer a chat question.
 *
 * <p>A short, deterministic lexical ranking is the right tool here: it needs no
 * schema change, no extra embedding round-trip per question, and is fully
 * reproducible. Resume passages still come from the existing pgvector retrieval,
 * which is where semantic matching actually matters.
 */
@Component
public class JobPassageSelector {

    private static final Pattern WORD = Pattern.compile("[a-z0-9+#./-]{3,}");
    private static final int MAX_PASSAGE_CHARS = 600;
    private static final int MAX_PASSAGES_CONSIDERED = 60;

    /** Common words that match everything and therefore rank nothing. */
    private static final Set<String> STOP_WORDS = new java.util.LinkedHashSet<>(List.of(
            "the", "and", "for", "with", "you", "your", "are", "our", "this", "that", "will",
            "have", "has", "not", "but", "can", "who", "what", "when", "how", "why", "all",
            "any", "job", "role", "work", "team", "company", "about", "they", "them", "from",
            "their", "would", "should", "could", "been", "were", "than", "then", "into", "over",
            "must", "may", "his", "her", "its", "use", "using", "used", "please", "help",
            "explain", "tell", "give", "show", "does", "done", "make", "made", "helpful"));

    /**
     * @param jdText full job description text
     * @param question the user's question, used as the ranking signal
     * @param limit   maximum passages to return
     */
    public List<String> select(String jdText, String question, int limit) {
        if (jdText == null || jdText.isBlank() || limit <= 0) {
            return List.of();
        }

        List<String> passages = splitPassages(jdText);
        if (passages.isEmpty()) {
            return List.of();
        }

        Set<String> queryTerms = terms(question);
        // Fall back to the skills under discussion when the question is short or
        // generic, so a bare "explain my score" still surfaces real requirements.
        List<String> ranked = new ArrayList<>(passages);

        if (!queryTerms.isEmpty()) {
            ranked.sort(Comparator.comparingInt((String p) -> overlapScore(terms(p), queryTerms)).reversed());
        }

        return ranked.stream()
                .filter(p -> p.length() > 40)
                .limit(limit)
                .toList();
    }

    private int overlapScore(Set<String> passageTerms, Set<String> queryTerms) {
        int score = 0;
        for (String t : queryTerms) {
            if (passageTerms.contains(t)) {
                score++;
            }
        }
        return score;
    }

    private List<String> splitPassages(String text) {
        List<String> passages = new ArrayList<>();
        for (String block : text.split("\\R{2,}")) {
            if (block.isBlank()) {
                continue;
            }
            // A single wall of text (no blank lines) still needs splitting,
            // otherwise one 4000-char block would be the only candidate.
            if (block.length() > MAX_PASSAGE_CHARS) {
                for (String line : block.split("\\R")) {
                    if (!line.isBlank()) {
                        passages.add(truncate(line.strip(), MAX_PASSAGE_CHARS));
                    }
                }
            } else {
                passages.add(block.strip());
            }
            if (passages.size() >= MAX_PASSAGES_CONSIDERED) {
                break;
            }
        }
        return passages;
    }

    private Set<String> terms(String text) {
        if (text == null) {
            return Set.of();
        }
        Set<String> found = new LinkedHashSet<>();
        var matcher = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (matcher.find()) {
            String term = matcher.group();
            if (!STOP_WORDS.contains(term)) {
                found.add(term);
            }
        }
        return found;
    }

    private String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }
}
