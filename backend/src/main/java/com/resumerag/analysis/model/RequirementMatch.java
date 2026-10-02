package com.resumerag.analysis.model;

import java.util.List;

/**
 * A requirement together with everything the engine could show for it.
 *
 * <p>This is the unit the report is built from. {@code resumeEvidence} and
 * {@code jdEvidence} hold the literal sentences that decided the outcome, so any
 * number in the report can be traced back to text a user can read - which is the
 * difference between an analysis and a verdict.
 *
 * <p>{@code confidence} is the engine's certainty in the <em>classification</em>,
 * not in the candidate. {@code evidenceStrength} is how good the evidence is.
 * They are separate because a strong piece of evidence can be ambiguous to
 * classify and a weak one obvious.
 */
public record RequirementMatch(
        int requirementIndex,
        String requirement,
        String normalizedRequirement,
        RequirementCategory category,
        RequirementImportance importance,
        MatchStatus status,
        double confidence,
        int evidenceStrength,
        MatchProvenance provenance,
        List<String> resumeEvidence,
        List<String> jdEvidence,
        String explanation
) {

    public RequirementMatch {
        resumeEvidence = resumeEvidence == null ? List.of() : List.copyOf(resumeEvidence);
        jdEvidence = jdEvidence == null ? List.of() : List.copyOf(jdEvidence);
    }

    /** True when this requirement contributes nothing to its category's score. */
    public boolean isUnsatisfied() {
        return status == MatchStatus.NOT_EXPLICITLY_MENTIONED || status == MatchStatus.CONFLICT;
    }
}
