package com.resumerag.analysis.model;

import java.util.List;

/**
 * The report header, as stored.
 *
 * <p>Persisted as one JSON document on the analysis row. The per-requirement
 * evidence lives in the normalised tables, where it can be queried; this is the
 * part a report needs on every page load - the score, its label, the experience
 * alignment and the advice - and reading it back as one row rather than
 * reassembling it avoids any chance of a re-read disagreeing with the original
 * analysis.
 *
 * <p>{@code scored} is false when the job description yielded no requirements. The
 * score is then 0, which a client must read as "not calculated" rather than as a
 * 0% match: showing a zero-percent verdict because a parse failed is a false
 * rejection of the candidate.
 */
public record AnalysisReportMeta(
        int overallScore,
        boolean scored,
        String matchLabel,
        Double assessedWeight,
        ExperienceAlignment experienceAlignment,
        List<String> recommendations
) {
    public AnalysisReportMeta {
        recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
    }
}
