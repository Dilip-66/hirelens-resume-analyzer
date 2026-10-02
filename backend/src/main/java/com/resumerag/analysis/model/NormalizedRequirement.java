package com.resumerag.analysis.model;

import java.util.List;

/**
 * A requirement reduced to canonical terms plus the logic that combines them.
 *
 * <p>{@link #canonicalKey()} is stable across the JD's wording - "REST API",
 * "REST APIs", "RESTful APIs" and "RESTful services" all collapse to
 * {@code REST_API} - which is what stops one capability being counted twice or
 * reported as a gap against a candidate who plainly has it.
 */
public record NormalizedRequirement(
        String canonicalKey,
        String displayName,
        CompoundOperator operator,
        List<NormalizedTerm> terms
) {

    public NormalizedRequirement {
        terms = terms == null ? List.of() : List.copyOf(terms);
    }

    public static NormalizedRequirement of(String canonicalKey, String displayName, NormalizedTerm term) {
        return new NormalizedRequirement(canonicalKey, displayName, CompoundOperator.NONE, List.of(term));
    }

    /**
     * Whether every term has to be satisfied.
     *
     * <p>{@link CompoundOperator#AND_OR} resolves to {@code false}: it is read as
     * a disjunction, since that is what job descriptions mean by it.
     */
    public boolean requiresAllTerms() {
        return operator == CompoundOperator.AND;
    }

    /** The fraction of terms satisfied, for weighting an AND group that is only partly met. */
    public double satisfactionRatio(List<String> satisfiedCanonicalKeys) {
        if (terms.isEmpty()) {
            return 0.0;
        }
        long satisfied = terms.stream()
                .filter(t -> satisfiedCanonicalKeys.contains(t.canonicalKey()))
                .count();
        return (double) satisfied / terms.size();
    }
}
