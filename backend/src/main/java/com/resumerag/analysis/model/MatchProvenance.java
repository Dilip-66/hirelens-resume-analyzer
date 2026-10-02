package com.resumerag.analysis.model;

/**
 * Where a match came from, kept separate from {@link MatchStatus} because the
 * same status can be honest or a guess.
 *
 * <p>A soft skill is the case that matters: "Debug issues and improve application
 * performance" is answered by "Reduced API response time by 30% through caching
 * and query optimization", which is real evidence of the performance half and
 * thin evidence of the debugging half. Reporting that flatly as EXPLICIT_MATCH
 * would overclaim, and reporting it as inferred without a confidence penalty
 * would understate it. Splitting the two lets the report say which is which.
 */
public enum MatchProvenance {

    /** The requirement is satisfied by language the resume actually uses. */
    EXPLICIT,

    /**
     * Satisfied through related evidence that the engine, not the resume, drew
     * the connection for. Carries a reduced confidence.
     */
    INFERRED,

    /** No evidence found. */
    NOT_EXPLICIT
}
