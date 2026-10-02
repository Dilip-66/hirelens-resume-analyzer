package com.resumerag.analysis.model;

/**
 * The scoreable dimensions, each with its own weight.
 *
 * <p>Enums rather than strings because the weight, the applicability rule and the
 * null-handling all hang off the identity, and three of them (projects,
 * education, ATS) are routinely unavailable - see {@link #scoredByEvidence()}.
 */
public enum ScoreCategory {

    REQUIRED_SKILLS(0.40, true),
    EXPERIENCE(0.20, true),
    RESPONSIBILITIES(0.15, true),
    PREFERRED_SKILLS(0.10, true),
    PROJECTS(0.05, false),
    EDUCATION(0.05, false),
    ATS(0.05, false);

    private final double defaultWeight;
    private final boolean scoredByEvidence;

    ScoreCategory(double defaultWeight, boolean scoredByEvidence) {
        this.defaultWeight = defaultWeight;
        this.scoredByEvidence = scoredByEvidence;
    }

    public double defaultWeight() {
        return defaultWeight;
    }

    /**
     * Whether this dimension is computed from what the resume actually says.
     *
     * <p>False for the dimensions that need machinery this pipeline does not have:
     * there is no project-to-requirement matcher, no degree-level comparator and
     * no ATS parser in the codebase. Their weights are reserved so the totals
     * still mean something, but they contribute nothing - and are reported as
     * {@code null}, not as a number. A fabricated 80% for education that was
     * never assessed is worse than admitting the dimension was skipped.
     */
    public boolean scoredByEvidence() {
        return scoredByEvidence;
    }
}
