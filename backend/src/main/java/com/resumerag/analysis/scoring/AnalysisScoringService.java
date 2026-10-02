package com.resumerag.analysis.scoring;

import com.resumerag.analysis.model.CategoryScore;
import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.MatchLabel;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoreCategory;
import com.resumerag.analysis.model.ScoredAnalysis;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Turns classified requirements into a score, and nothing else.
 *
 * <p>Deterministic by construction: the same requirements and the same evidence
 * always produce the same numbers, and no model is involved. That is the whole
 * reason this service exists separately from the narrative generator. A screening
 * score that a language model can move by a few points between two runs of the
 * same input cannot be defended to a candidate, and cannot be regression-tested
 * either - which is why the previous pipeline, where the model returned
 * {@code matchScore} straight into the database, had no test that meant anything.
 *
 * <p>Two properties are enforced here rather than left to configuration:
 *
 * <ul>
 *   <li><b>Nothing is fabricated.</b> A dimension with no assessable data is
 *       reported as {@code null} and excluded from the mean, not as a zero and
 *       not as an average of nothing. The weights of the dimensions that could be
 *       assessed are renormalised, so an unassessed dimension neither penalises
 *       the candidate nor inflates the result.</li>
 *   <li><b>Preferred skills cannot rescue a missing core.</b> They sit in their
 *       own category at a fraction of the required-skills weight, so no amount of
 *       bonus-skill coverage can offset not having the stack the job is built
 *       on.</li>
 * </ul>
 */
@Service
public class AnalysisScoringService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisScoringService.class);

    private final ScoringConfiguration configuration;

    public AnalysisScoringService(ScoringConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Scores one analysis.
     *
     * @param matches            every requirement, classified
     * @param experience         the experience alignment, already computed
     * @param requirements       the requirements the matches refer to, so the
     *                           result is self-contained for the API
     */
    public ScoredAnalysis score(List<RequirementMatch> matches,
                                ExperienceAlignment experience,
                                List<com.resumerag.analysis.model.Requirement> requirements) {
        return score(matches, experience, requirements, null, null, null);
    }

    /**
     * Scores one analysis, including the dimensions that are not requirement
     * matches.
     *
     * @param projects  per-project relevance, or {@code null} when the resume
     *                  describes no projects
     * @param education the education comparison, or {@code null} when the job
     *                  description states no education requirement
     * @param ats       the quality assessment, or {@code null} when there was too
     *                  little text to assess
     */
    public ScoredAnalysis score(List<RequirementMatch> matches,
                                ExperienceAlignment experience,
                                List<com.resumerag.analysis.model.Requirement> requirements,
                                List<com.resumerag.analysis.model.ProjectAssessment> projects,
                                com.resumerag.analysis.assess.EducationAssessmentService.Assessment education,
                                com.resumerag.analysis.assess.AtsQualityService.Assessment ats) {
        Map<ScoreCategory, CategoryScore> categoryScores = new EnumMap<>(ScoreCategory.class);

        categoryScores.put(ScoreCategory.REQUIRED_SKILLS,
                skillCategory(ScoreCategory.REQUIRED_SKILLS, RequirementCategory.REQUIRED_SKILL, matches));
        categoryScores.put(ScoreCategory.PREFERRED_SKILLS,
                skillCategory(ScoreCategory.PREFERRED_SKILLS, RequirementCategory.PREFERRED_SKILL, matches));
        categoryScores.put(ScoreCategory.RESPONSIBILITIES,
                skillCategory(ScoreCategory.RESPONSIBILITIES, RequirementCategory.RESPONSIBILITY, matches));
        categoryScores.put(ScoreCategory.EXPERIENCE, experienceScore(experience));
        categoryScores.put(ScoreCategory.PROJECTS, projectsScore(projects));
        categoryScores.put(ScoreCategory.EDUCATION, educationScore(education));
        categoryScores.put(ScoreCategory.ATS, atsScore(ats));

        double assessedWeight = categoryScores.values().stream()
                .filter(CategoryScore::isAssessed)
                .mapToDouble(CategoryScore::weight)
                .sum();
        double weightedTotal = categoryScores.values().stream()
                .filter(CategoryScore::isAssessed)
                .mapToDouble(c -> c.weight() * c.score())
                .sum();

        // Renormalised over assessed dimensions only. Without this an unassessed
        // category would silently drag the score toward zero, which would make the
        // score a function of what the engine failed to measure.
        int overall = assessedWeight <= 0 ? 0
                : (int) Math.round(weightedTotal / assessedWeight);

        overall = Math.max(0, Math.min(100, overall));
        MatchLabel label = configuration.labelFor(overall);

        List<CategoryScore> ordered = new ArrayList<>(categoryScores.values());
        int assessedCount = (int) matches.stream().filter(m -> m.category() == RequirementCategory.REQUIRED_SKILL
                || m.category() == RequirementCategory.PREFERRED_SKILL
                || m.category() == RequirementCategory.RESPONSIBILITY).count();

        log.debug("Scores: required={} responsibilities={} preferred={} experience={} overall={} ({})",
                display(categoryScores.get(ScoreCategory.REQUIRED_SKILLS)),
                display(categoryScores.get(ScoreCategory.RESPONSIBILITIES)),
                display(categoryScores.get(ScoreCategory.PREFERRED_SKILLS)),
                display(categoryScores.get(ScoreCategory.EXPERIENCE)),
                overall, label.displayName());

        return new ScoredAnalysis(overall, label, ordered, experience, matches, requirements,
                round(assessedWeight), assessedCount,
                new ScoredAnalysis.AssessorDetail(projects, education, ats));
    }

    /**
     * Scores one requirement category as an importance-weighted mean.
     *
     * <p>Each requirement contributes {@code credit x confidence}, scaled by how
     * load-bearing it is. Both factors are there for a reason: the credit
     * separates "demonstrated" from "listed" from "absent", and the confidence
     * keeps a classification the engine is unsure of from counting as much as one
     * it is sure of.
     *
     * <p>An absent requirement scores zero rather than dropping out of the average.
     * A missing requirement is a finding, and excluding it would let a job
     * description with twenty requirements the candidate happens to meet score
     * higher than one with two, which is precisely backwards.
     */
    private CategoryScore skillCategory(ScoreCategory scoreCategory,
                                        RequirementCategory requirementCategory,
                                        List<RequirementMatch> matches) {
        List<RequirementMatch> inCategory = matches.stream()
                .filter(m -> m.category() == requirementCategory)
                .toList();
        if (inCategory.isEmpty()) {
            return CategoryScore.notAssessed(scoreCategory, configuration.weightFor(scoreCategory),
                    "No requirements of this kind were found in the job description.");
        }

        double weightSum = 0.0;
        double earned = 0.0;
        for (RequirementMatch match : inCategory) {
            double weight = importanceWeight(match.importance());
            weightSum += weight;
            earned += weight * creditFor(match);
        }
        double score = weightSum <= 0 ? 0 : (earned / weightSum) * 100;
        return new CategoryScore(scoreCategory, round(score), configuration.weightFor(scoreCategory),
                round(weightSum <= 0 ? 0 : (earned / weightSum) * configuration.weightFor(scoreCategory)),
                inCategory.size(), null);
    }

    /**
     * What one match is worth, before its category weight.
     *
     * <p>Zero for anything unsatisfied, regardless of confidence: a low confidence
     * on an absent requirement is still an absent requirement.
     */
    private double creditFor(RequirementMatch match) {
        if (match.status() == MatchStatus.NOT_EXPLICITLY_MENTIONED
                || match.status() == MatchStatus.CONFLICT) {
            return 0.0;
        }
        double credit = configuration.creditFor(match.status());
        double confidence = Math.max(configuration.getConfidenceFloor(),
                Math.min(configuration.getConfidenceCeiling(), match.confidence()));
        return credit * confidence;
    }

    /** How much a requirement is worth within its own category. */
    private double importanceWeight(RequirementImportance importance) {
        return switch (importance) {
            case CRITICAL -> configuration.getCriticalImportanceWeight();
            case HIGH -> configuration.getHighImportanceWeight();
            case MEDIUM -> configuration.getMediumImportanceWeight();
            case LOW -> configuration.getLowImportanceWeight();
        };
    }

    /**
     * The experience dimension, discounted when the figure was derived rather than
     * stated.
     *
     * <p>A year count computed from employment dates is arithmetic over the
     * document, not a statement by the candidate: contract work, a gap they chose
     * not to explain, or a typo in a date all move it. It is still far more
     * informative than nothing - the benchmark showed that returning null for the
     * majority of resumes silently removed a 20%-weighted dimension - but it is
     * held to a lower ceiling so it can never be treated as authoritative.
     */
    private CategoryScore experienceScore(ExperienceAlignment alignment) {
        if (alignment == null || alignment.score() == null) {
            return CategoryScore.notAssessed(ScoreCategory.EXPERIENCE,
                    configuration.weightFor(ScoreCategory.EXPERIENCE),
                    alignment == null
                            ? "No experience requirement was extracted from the job description."
                            : alignment.explanation());
        }
        double score = alignment.score();
        if (!alignment.yearsWereStated()) {
            score = Math.min(score, configuration.getInferredExperienceCeiling());
        }
        return new CategoryScore(ScoreCategory.EXPERIENCE, round(score),
                configuration.weightFor(ScoreCategory.EXPERIENCE),
                round((score / 100) * configuration.weightFor(ScoreCategory.EXPERIENCE)),
                1, alignment.explanation());
    }

    /**
     * The projects dimension, from the projects the resume actually describes.
     *
     * <p>Unassessed rather than zero when the resume describes no projects. A
     * candidate who lists no projects has not demonstrated poor project work, and
     * scoring that as zero would let a formatting choice decide five percent of
     * the result.
     */
    private CategoryScore projectsScore(List<com.resumerag.analysis.model.ProjectAssessment> projects) {
        if (projects == null || projects.isEmpty()) {
            return CategoryScore.notAssessed(ScoreCategory.PROJECTS,
                    configuration.weightFor(ScoreCategory.PROJECTS),
                    "Not scored: this resume does not describe any projects, so there is no project "
                            + "evidence to measure against the role.");
        }
        double earned = projects.stream()
                .mapToDouble(com.resumerag.analysis.model.ProjectAssessment::relevanceScore)
                .average()
                .orElse(0.0);
        return new CategoryScore(ScoreCategory.PROJECTS, round(earned),
                configuration.weightFor(ScoreCategory.PROJECTS),
                round((earned / 100) * configuration.weightFor(ScoreCategory.PROJECTS)),
                projects.size(),
                "Averaged across " + projects.size() + " project"
                        + (projects.size() == 1 ? "" : "s") + " described in the resume.");
    }

    private CategoryScore educationScore(
            com.resumerag.analysis.assess.EducationAssessmentService.Assessment education) {
        if (education == null || !education.assessed() || education.score() == null) {
            return CategoryScore.notAssessed(ScoreCategory.EDUCATION,
                    configuration.weightFor(ScoreCategory.EDUCATION),
                    education == null
                            ? "No education requirement was found in this job description."
                            : education.note());
        }
        return new CategoryScore(ScoreCategory.EDUCATION, education.score(),
                configuration.weightFor(ScoreCategory.EDUCATION),
                round((education.score() / 100) * configuration.weightFor(ScoreCategory.EDUCATION)),
                education.outcomes().size(),
                education.outcomes().isEmpty() ? null
                        : education.outcomes().get(0).explanation());
    }

    private CategoryScore atsScore(com.resumerag.analysis.assess.AtsQualityService.Assessment ats) {
        if (ats == null || !ats.assessed() || ats.score() == null) {
            return CategoryScore.notAssessed(ScoreCategory.ATS,
                    configuration.weightFor(ScoreCategory.ATS),
                    ats == null ? "Not assessed: no resume text was available."
                            : ats.note());
        }
        return new CategoryScore(ScoreCategory.ATS, ats.score(),
                configuration.weightFor(ScoreCategory.ATS),
                round((ats.score() / 100) * configuration.weightFor(ScoreCategory.ATS)),
                ats.factors().size(), ats.note());
    }

    private String display(CategoryScore score) {
        return score == null || score.score() == null ? "n/a" : String.valueOf(score.score());
    }

    /**
     * Rounds to the configured precision.
     *
     * <p>Half-up, explicitly. {@code String.format} rounds half-to-even, which
     * makes the same score print differently depending on the magnitude it landed
     * on - and a score that renders differently for identical inputs is not a
     * deterministic score.
     */
    private double round(double value) {
        int decimals = (int) Math.max(0, Math.round(configuration.getRoundingDecimals()));
        return BigDecimal.valueOf(value)
                .setScale(decimals, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
