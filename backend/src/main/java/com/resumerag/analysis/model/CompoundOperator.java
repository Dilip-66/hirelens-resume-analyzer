package com.resumerag.analysis.model;

/**
 * How the parts of a compound requirement relate to each other.
 *
 * <p>"JavaScript and/or TypeScript" is one requirement, not two mandatory ones.
 * Split it naively and a candidate who writes only TypeScript is reported as
 * missing a language they demonstrably use, which is the single most misleading
 * thing a resume screener can do.
 */
public enum CompoundOperator {

    /** A single term - no disjunction to resolve. */
    NONE,

    /** Every term is needed: "Git and GitHub". */
    AND,

    /** Any one term satisfies it: "AWS or cloud platforms". */
    OR,

    /**
     * "and/or" as written in the job description.
     *
     * <p>Read as OR. In practice this phrase is used to say "one of these is
     * enough", and treating it as AND invents a requirement the employer did not
     * state - which the engine must never do.
     */
    AND_OR
}
