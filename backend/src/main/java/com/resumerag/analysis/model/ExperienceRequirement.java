package com.resumerag.analysis.model;

import java.util.List;

/**
 * A years-of-experience constraint read out of the job description, normalised.
 *
 * <p>{@code maxYears} is {@code null} for open-ended constraints ("5+ years"), not
 * zero - "at least 5" and "exactly 5" are different requirements and collapsing
 * them would fail a senior candidate against a senior role.
 *
 * <p>Two identical constraints mentioned twice in one JD ("Experience: 1-3 years"
 * in the header and "1-3 years of software development experience" in the skills
 * list) are one requirement, deduplicated by the extractor.
 */
public record ExperienceRequirement(
        Double minYears,
        Double maxYears,
        String sourceText
) {

    public boolean hasMinimum() {
        return minYears != null;
    }

    public boolean hasMaximum() {
        return maxYears != null;
    }

    /** Stable key for deduplication and persistence. */
    public String key() {
        return (minYears == null ? "*" : trim(minYears)) + "-"
                + (maxYears == null ? "*" : trim(maxYears));
    }

    /**
     * "1-3 years", "1+ years", "at least 3 years" - rendered for the report.
     */
    public String describe() {
        if (minYears != null && maxYears != null && minYears.equals(maxYears)) {
            return trim(minYears) + " years";
        }
        if (minYears != null && maxYears != null) {
            return trim(minYears) + "-" + trim(maxYears) + " years";
        }
        if (minYears != null) {
            return trim(minYears) + "+ years";
        }
        if (maxYears != null) {
            return "up to " + trim(maxYears) + " years";
        }
        return "any experience";
    }

    private static String trim(double value) {
        if (value == Math.rint(value)) {
            return Long.toString((long) value);
        }
        return Double.toString(value);
    }

    /** All constraints found in a JD, deduplicated, most specific first. */
    public static List<ExperienceRequirement> dedupe(List<ExperienceRequirement> found) {
        return found == null ? List.of() : found.stream()
                .filter(r -> r != null && (r.hasMinimum() || r.hasMaximum()))
                .filter(r -> r.key() != null)
                .distinct()
                .toList();
    }
}
