package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.ExperienceRequirement;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;

import java.util.List;

/**
 * Everything extracted from one job description, before the resume is consulted.
 *
 * <p>The experience constraint is held as its own field because years of
 * experience is a range over a number, not a matchable phrase, and is scored on
 * a different axis from the requirement list.
 */
public record JobDescriptionProfile(
        List<Requirement> requirements,
        ExperienceRequirement experienceRequirement
) {
    public JobDescriptionProfile {
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    public List<Requirement> inCategory(RequirementCategory category) {
        return requirements.stream().filter(r -> r.category() == category).toList();
    }

    public long countIn(RequirementCategory category) {
        return requirements.stream().filter(r -> r.category() == category).count();
    }
}
