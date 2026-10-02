package com.resumerag.analysis.model;

/**
 * How the candidate's experience lines up with the range the job asked for.
 *
 * <p>Two things are kept apart here on purpose, because conflating them is how a
 * screening tool ends up stating something the candidate never said:
 *
 * <ul>
 *   <li>{@code candidateYears} - the number compared against the band.</li>
 *   <li>{@code yearsWereStated} - whether the candidate wrote that number, or
 *       whether the engine derived it from employment dates.</li>
 * </ul>
 *
 * <p>A stated figure is used as-is. Dates are only consulted when the resume
 * states nothing, because a date range is arithmetic over the document rather
 * than a statement by its author - and the result carries a confidence discount
 * in the scoring engine and an "estimated from employment dates" sentence in the
 * report. Both are still far better than returning nothing: the benchmark showed
 * 17 of 30 real scenarios had no stated figure, which silently removed a
 * 20%-weighted dimension and let a one-year candidate score exactly as though
 * nobody had asked about experience at all.
 */
public record ExperienceAlignment(
        Double candidateYears,
        boolean yearsWereStated,
        Double requiredMinYears,
        Double requiredMaxYears,
        Status status,
        Double score,
        String explanation
) {

    /** The outcome of comparing a candidate's experience against a stated range. */
    public enum Status {

        /** Inside the requested range. */
        WITHIN_RANGE,

        /** Below the stated minimum. */
        BELOW_RANGE,

        /**
         * Above the stated maximum.
         *
         * <p>Not a shortfall, and not scored as one. Exceeding a ceiling means
         * the candidate is more experienced than the band, which on a
         * "1-3 years" requirement describes a stronger candidate, not a
         * mismatched one.
         */
        ABOVE_RANGE,

        /** The JD stated no experience constraint. */
        NOT_SPECIFIED,

        /** Neither the resume nor its dates support a figure. */
        CANDIDATE_UNKNOWN
    }

    public static ExperienceAlignment unknown() {
        return new ExperienceAlignment(null, false, null, null, Status.CANDIDATE_UNKNOWN, null,
                "The resume does not state a figure for total years of experience.");
    }
}
