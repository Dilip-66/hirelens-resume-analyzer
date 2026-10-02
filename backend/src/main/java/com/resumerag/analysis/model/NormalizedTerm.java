package com.resumerag.analysis.model;

import java.util.List;

/**
 * One alternative within a compound requirement, reduced to something we can
 * look for.
 *
 * <p>{@code patterns} are lowercase literal surface forms. They are matched on
 * word boundaries, not as substrings, so "Go" does not match "going" and "AI" does
 * not match "said". Every pattern maps to exactly one canonical key - that is the
 * whole point, and it is why unrelated technologies are never folded together.
 */
public record NormalizedTerm(
        String canonicalKey,
        String displayName,
        List<String> patterns
) {
    public NormalizedTerm {
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }
}
