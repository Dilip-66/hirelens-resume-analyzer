package com.resumerag.dto.analysis;

import com.resumerag.analysis.model.ScoreCategory;

import java.util.List;

/**
 * The score, dimension by dimension, with the dimensions that could not be
 * measured present and {@code null}.
 *
 * <p>Two properties are load-bearing. The overall score is a weighted mean over
 * the dimensions that <em>were</em> assessed, so an unmeasured dimension neither
 * penalises the candidate nor inflates the result. And an unmeasured dimension is
 * reported as {@code null} rather than as a number, because a plausible-looking
 * 80% for a dimension nothing ever assessed is worse than admitting the gap - a
 * reader cannot tell an invented score from a real one.
 */
public record ScoreBreakdownResponse(
        Double requiredSkills,
        Double experience,
        Double responsibilities,
        Double preferredSkills,
        Double projects,
        Double education,
        Double ats,
        List<CategoryScoreResponse> categories
) {
    public ScoreBreakdownResponse {
        categories = categories == null ? List.of() : List.copyOf(categories);
    }

    public static ScoreBreakdownResponse from(List<com.resumerag.analysis.model.CategoryScore> scores) {
        return new ScoreBreakdownResponse(
                scoreOf(scores, ScoreCategory.REQUIRED_SKILLS),
                scoreOf(scores, ScoreCategory.EXPERIENCE),
                scoreOf(scores, ScoreCategory.RESPONSIBILITIES),
                scoreOf(scores, ScoreCategory.PREFERRED_SKILLS),
                scoreOf(scores, ScoreCategory.PROJECTS),
                scoreOf(scores, ScoreCategory.EDUCATION),
                scoreOf(scores, ScoreCategory.ATS),
                scores == null ? List.of()
                        : scores.stream().map(CategoryScoreResponse::from).toList());
    }

    /**
     * The score for one dimension, or {@code null} when it was not assessed.
     *
     * <p>Null-safe by hand rather than through {@code findFirst()}: the value being
     * looked up <em>is</em> null for every unassessed dimension, and
     * {@code Optional.findFirst()} throws on a null element. The whole design here
     * depends on null meaning "not measured", so a lookup that cannot handle null
     * would fail on precisely the case that matters.
     */
    private static Double scoreOf(List<com.resumerag.analysis.model.CategoryScore> scores, ScoreCategory category) {
        if (scores == null) {
            return null;
        }
        for (com.resumerag.analysis.model.CategoryScore score : scores) {
            if (score != null && score.category() == category) {
                return score.score();
            }
        }
        return null;
    }
}
