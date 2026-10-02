package com.resumerag.dto.analysis;

import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;

import java.util.List;

/**
 * One requirement and everything the engine concluded about it.
 *
 * <p>This is the unit that makes a report arguable. Every status here is backed by
 * {@code resumeEvidence} - the sentences from the candidate's own resume that
 * decided it - and by {@code jdEvidence}, the line in the job description that
 * created the requirement. A user can check any number in the report against
 * text they can read, which is the difference between an analysis and an oracle.
 *
 * <p>Note what {@code NOT_EXPLICITLY_MENTIONED} does not say. It is not a claim
 * that the candidate lacks the skill, and the wording is kept that way
 * throughout.
 */
public record RequirementMatchResponse(
        int index,
        String name,
        String normalizedName,
        RequirementCategory category,
        RequirementImportance importance,
        String status,
        double confidence,
        int evidenceStrength,
        String provenance,
        List<String> resumeEvidence,
        List<String> jdEvidence,
        String explanation
) {
    public RequirementMatchResponse {
        resumeEvidence = resumeEvidence == null ? List.of() : List.copyOf(resumeEvidence);
        jdEvidence = jdEvidence == null ? List.of() : List.copyOf(jdEvidence);
    }
}
