package com.resumerag.analysis.model;

import java.util.List;

/**
 * One requirement as extracted from the job description, before any comparison
 * with the resume.
 *
 * <p>{@code index} is assigned during extraction and used as the stable ordinal
 * for persistence and for the API, so requirement 7 is the same requirement
 * across a re-read of the stored analysis.
 *
 * <p>{@code explicitRequirement} records whether the JD stated this as a
 * requirement in so many words, as opposed to the engine inferring it. The
 * distinction matters for the report: an inferred requirement is shown as the
 * engine's reading, not passed off as the employer's wording.
 */
public record Requirement(
        int index,
        String name,
        RequirementCategory category,
        RequirementImportance importance,
        DemandLevel demand,
        String normalizedName,
        List<String> synonyms,
        String sourceText,
        boolean explicitRequirement,
        NormalizedRequirement normalization
) {
    public Requirement {
        synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
    }
}
