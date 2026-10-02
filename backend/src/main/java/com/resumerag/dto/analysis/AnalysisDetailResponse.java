package com.resumerag.dto.analysis;

import java.util.List;

/**
 * The explainable part of an analysis, alongside the original flat fields.
 *
 * <p>Additive by design. {@code matchScore}, {@code matchedSkills} and
 * {@code missingSkills} still exist and still mean what they meant, because a
 * report created before this change has to keep rendering and an existing client
 * must not start seeing fields it does not understand. This block is the richer
 * view: every requirement, its status, the evidence on both sides, the score
 * dimension it fed, and whether the dimension was measured at all.
 *
 * <p>{@code scored} is false for an analysis that could not be measured - a job
 * description with no extractable requirements. The score is then 0, which must
 * be read as "not calculated" rather than as a 0% match: a client showing a
 * zero-percent verdict because a parse failed is a false rejection.
 */
public record AnalysisDetailResponse(
        boolean scored,
        String matchLabel,
        Double overallScore,
        ScoreBreakdownResponse scoreBreakdown,
        ExperienceAlignmentResponse experienceAlignment,
        List<RequirementMatchResponse> requirements,
        List<String> recommendations,
        Integer requirementsAnalysed,
        Integer requirementsWithEvidence
) {
    public AnalysisDetailResponse {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
