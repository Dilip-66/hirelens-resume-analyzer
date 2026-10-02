package com.resumerag.analysis.model;

/**
 * How much a single requirement is worth <em>within its own category</em>.
 *
 * <p>Importance never moves a requirement between categories - that is
 * {@link RequirementCategory}'s job, and it carries far more weight. Within the
 * required-skills bucket, "Strong programming skills in Java" and "Familiarity
 * with Git" are both mandatory, but they are not equally load-bearing for the
 * role, and averaging them as equals understates the real cost of missing the
 * first one.
 *
 * <p>The multipliers live in {@code ScoringConfiguration}, not here: the ordinal
 * is the meaning, the number is a tunable.
 */
public enum RequirementImportance {

    /** Load-bearing for the role. Losing it is disqualifying, not a deduction. */
    CRITICAL,

    /** Genuinely required and expected. */
    HIGH,

    /** Expected, but the role is still workable without it. */
    MEDIUM,

    /** Nice to have, or an extraction we could not weigh confidently. */
    LOW
}
