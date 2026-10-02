package com.resumerag.analysis.model;

import java.util.List;

/**
 * The narrative that accompanies the deterministic numbers.
 *
 * <p>Deliberately holds no score. The previous pipeline let the model return
 * {@code matchScore} straight into the database, so the number a user saw was
 * whatever a 7B model felt like emitting for a prompt - unauditable, and free to
 * contradict the skill scan in the same response. Narrative is the one thing a
 * model is genuinely better at, so that is the only thing it is left to write.
 */
public record AnalysisNarrative(
        String summary,
        List<String> strengths,
        List<String> gaps
) {
    public AnalysisNarrative {
        strengths = strengths == null ? List.of() : List.copyOf(strengths);
        gaps = gaps == null ? List.of() : List.copyOf(gaps);
    }
}
