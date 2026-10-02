package com.resumerag.analysis.model;

/**
 * The plain-language band a score falls into.
 *
 * <p>Thresholds live in {@code ScoringConfiguration} - a band is a product
 * decision, not a constant buried in a scoring method.
 */
public enum MatchLabel {

    EXCELLENT_MATCH("Excellent Match"),
    STRONG_MATCH("Strong Match"),
    GOOD_MATCH("Good Match"),
    MODERATE_MATCH("Moderate Match"),
    WEAK_MATCH("Weak Match");

    private final String displayName;

    MatchLabel(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
