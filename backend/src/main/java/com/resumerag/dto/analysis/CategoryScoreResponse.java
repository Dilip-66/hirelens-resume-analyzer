package com.resumerag.dto.analysis;

import com.resumerag.analysis.model.CategoryScore;
import com.resumerag.analysis.model.ScoreCategory;

/**
 * One dimension, with its weight and - when it was not assessed - why.
 *
 * <p>{@code note} is what turns "projects: null" from a gap in the response into
 * an explanation. A reader who asks why a dimension is missing gets an answer
 * rather than having to guess whether it was skipped or broken.
 */
public record CategoryScoreResponse(
        ScoreCategory category,
        Double score,
        double weight,
        Double weightedScore,
        int requirementsConsidered,
        boolean assessed,
        String note
) {
    public static CategoryScoreResponse from(CategoryScore score) {
        return new CategoryScoreResponse(
                score.category(),
                score.score(),
                score.weight(),
                score.weightedScore(),
                score.requirementsConsidered(),
                score.isAssessed(),
                score.note());
    }
}
