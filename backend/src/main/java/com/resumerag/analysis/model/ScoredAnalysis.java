package com.resumerag.analysis.model;

import java.util.List;

/**
 * The full deterministic result of scoring one resume against one job
 * description.
 *
 * <p>Deterministic in the strict sense: the same resume and JD produce the same
 * numbers on every run, with no model call anywhere in the path. That is what
 * makes a score arguable rather than merely delivered - and it is why the LLM is
 * confined to writing the summary sentence.
 */
public record ScoredAnalysis(
        int overallScore,
        MatchLabel matchLabel,
        List<CategoryScore> categoryScores,
        ExperienceAlignment experienceAlignment,
        List<RequirementMatch> matches,
        List<Requirement> requirements,
        double assessedWeight,
        int requirementsAssessed,
        /**
         * Evidence for the three dimensions that are not requirement matches.
         *
         * <p>Held on the result rather than the score because they are the audit
         * trail, not arithmetic: the numbers are in {@code categoryScores} and the
         * reasoning is here. Each is absent when the dimension could not be
         * assessed, which is a first-class outcome and not a failure.
         */
        AssessorDetail detail
) {

    public ScoredAnalysis {
        categoryScores = categoryScores == null ? List.of() : List.copyOf(categoryScores);
        matches = matches == null ? List.of() : List.copyOf(matches);
        requirements = requirements == null ? List.of() : List.copyOf(requirements);
    }

    /**
     * The non-requirement assessments, for persistence and for the API.
     *
     * @param projects  the projects the resume describes and how they score, or
     *                  {@code null} when it describes none
     * @param education the education comparison, or {@code null} when the job
     *                  description states no education requirement
     * @param ats       the factor-by-factor quality assessment, or {@code null}
     *                  when there was too little text to assess
     */
    public record AssessorDetail(
            List<com.resumerag.analysis.model.ProjectAssessment> projects,
            com.resumerag.analysis.assess.EducationAssessmentService.Assessment education,
            com.resumerag.analysis.assess.AtsQualityService.Assessment ats
    ) {
        public AssessorDetail {
            projects = projects == null ? null : List.copyOf(projects);
        }
    }

    public CategoryScore category(ScoreCategory category) {
        return categoryScores.stream()
                .filter(c -> c.category() == category)
                .findFirst()
                .orElse(null);
    }

    public List<RequirementMatch> matchesIn(RequirementCategory category) {
        return matches.stream().filter(m -> m.category() == category).toList();
    }
}
