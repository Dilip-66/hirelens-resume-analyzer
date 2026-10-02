package com.resumerag.dto.analysis;

import com.resumerag.analysis.AnalysisEngine;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoredAnalysis;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Projects the analysis model onto the API shape.
 *
 * <p>Keeps the domain records free of JSON concerns and keeps the DTO free of
 * scoring logic. {@code null} is a real value throughout: an unassessed
 * dimension stays {@code null} on the way out, because turning it into a zero
 * here would undo the whole point of the breakdown.
 */
@Component
public class AnalysisDetailAssembler {

    public AnalysisDetailResponse from(AnalysisEngine.Result result, List<String> recommendations) {
        if (result == null) {
            return null;
        }
        ScoredAnalysis scored = result.scored();
        List<RequirementMatch> matches = scored.matches();

        return new AnalysisDetailResponse(
                scored.overallScore() > 0 || !matches.isEmpty(),
                scored.matchLabel().displayName(),
                matches.isEmpty() ? null : (double) scored.overallScore(),
                ScoreBreakdownResponse.from(scored.categoryScores()),
                ExperienceAlignmentResponse.from(scored.experienceAlignment()),
                matches.stream().map(AnalysisDetailAssembler::toResponse).toList(),
                recommendations == null ? List.of() : List.copyOf(recommendations),
                matches.size(),
                (int) matches.stream().filter(m -> !m.resumeEvidence().isEmpty()).count());
    }

    private static RequirementMatchResponse toResponse(RequirementMatch match) {
        return new RequirementMatchResponse(
                match.requirementIndex(),
                match.requirement(),
                match.normalizedRequirement(),
                match.category(),
                match.importance(),
                match.status().name(),
                match.confidence(),
                match.evidenceStrength(),
                match.provenance().name(),
                match.resumeEvidence(),
                match.jdEvidence(),
                match.explanation());
    }
}
