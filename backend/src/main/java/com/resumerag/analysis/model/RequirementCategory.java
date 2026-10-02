package com.resumerag.analysis.model;

/**
 * What kind of thing the job description asked for.
 *
 * <p>The category decides which bucket of the weighted score a requirement falls
 * into, and it is what stops a required skill and a "nice to have" from being
 * averaged together. A flat keyword list cannot distinguish them, which is how a
 * candidate that satisfies 10 of 12 required skills but 0 of 8 preferred skills
 * ends up with the same verdict as one who satisfies 6 of 12.
 */
public enum RequirementCategory {

    /** The role cannot be done without it. Missing one of these must hurt. */
    REQUIRED_SKILL,

    /**
     * Genuinely optional. A bonus, not a filter - weighted so it can never
     * manufacture a good score for a candidate who misses the required stack,
     * and can never sink a candidate who has it.
     */
    PREFERRED_SKILL,

    /** "1-3 years of software development experience" - a range, not a keyword. */
    EXPERIENCE,

    /** A duty of the job: "Design and integrate RESTful APIs." */
    RESPONSIBILITY,

    /** A degree, certification or formal qualification. */
    EDUCATION,

    /**
     * A way of working: communication, teamwork, problem solving. These are the
     * requirements most likely to be overclaimed, because a single suggestive
     * adjective on a resume gets read as proof, so they are scored from stated
     * evidence only and any inference is labelled as such.
     */
    SOFT_SKILL,

    /** A requirement we extracted but could not classify confidently. */
    GENERAL
}
