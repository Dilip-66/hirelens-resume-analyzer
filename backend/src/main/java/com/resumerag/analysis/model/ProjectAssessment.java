package com.resumerag.analysis.model;

import java.util.List;

/**
 * One project a resume describes, and how it stands against the role.
 *
 * <p>Kept separate from the requirement model because a project is not a
 * requirement. The job description never asks for "the Ledger Service"; it asks
 * for Spring Boot and REST API experience, and a project is the evidence that
 * those were applied to something. Conflating the two would put entries in a
 * report that no employer asked for.
 */
public record ProjectAssessment(
        String title,
        List<String> descriptions,
        List<String> technologies,
        boolean hasQuantifiedOutcome,
        double technologyRelevance,
        double responsibilityRelevance,
        double relevanceScore
) {

    public ProjectAssessment {
        descriptions = descriptions == null ? List.of() : List.copyOf(descriptions);
        technologies = technologies == null ? List.of() : List.copyOf(technologies);
    }

    /**
     * How closely a project speaks to the role, as three separable factors.
     *
     * <p>Separate rather than one number because a project can match on
     * technology and miss on substance, and collapsing that into a single score
     * hides the difference from the person reading their own report.
     *
     * @param technologyRelevance      share of the role's required technologies the project uses
     * @param responsibilityRelevance  how much of the project's wording aligns with the role's duties
     */
    public record Relevance(
            double technologyRelevance,
            double responsibilityRelevance,
            boolean hasQuantifiedOutcome,
            double score
    ) {}
}
