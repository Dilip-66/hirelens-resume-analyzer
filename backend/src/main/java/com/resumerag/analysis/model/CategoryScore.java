package com.resumerag.analysis.model;

/**
 * One dimension's contribution to the overall score.
 *
 * <p>{@code score} is {@code null} when the dimension could not be assessed. That
 * is a first-class outcome, not a missing value: the overall score is then
 * computed over the dimensions that could be, renormalised so the weights still
 * total 100%. Reporting an unassessed dimension as 0 would silently drag every
 * score down; reporting it as an average would invent evidence.
 */
public record CategoryScore(
        ScoreCategory category,
        Double score,
        double weight,
        Double weightedScore,
        int requirementsConsidered,
        String note
) {

    public static CategoryScore notAssessed(ScoreCategory category, double weight, String note) {
        return new CategoryScore(category, null, weight, null, 0, note);
    }

    public boolean isAssessed() {
        return score != null;
    }
}
