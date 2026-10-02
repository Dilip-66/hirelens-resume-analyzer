package com.resumerag.dto.analysis;

import com.resumerag.analysis.model.ExperienceAlignment;

/**
 * How the candidate's stated experience compares with the range the role asked
 * for.
 *
 * <p>A separate block rather than a line among the skills, because years of
 * experience is a range over a number and not a phrase with evidence attached.
 * "2.5 years against a 1-3 year requirement" is arithmetic, and reporting it
 * alongside a 2% keyword overlap would imply a similarity that does not exist.
 */
public record ExperienceAlignmentResponse(
        Double candidateYears,
        Double requiredMinYears,
        Double requiredMaxYears,
        ExperienceAlignment.Status status,
        Double score,
        String explanation
) {
    public static ExperienceAlignmentResponse from(ExperienceAlignment alignment) {
        if (alignment == null) {
            return null;
        }
        return new ExperienceAlignmentResponse(
                alignment.candidateYears(),
                alignment.requiredMinYears(),
                alignment.requiredMaxYears(),
                alignment.status(),
                alignment.score(),
                alignment.explanation());
    }
}
