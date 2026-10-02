package com.resumerag.analysis;

import com.resumerag.analysis.assess.AtsQualityService;
import com.resumerag.analysis.assess.EducationAssessmentService;
import com.resumerag.analysis.assess.ProjectRelevanceService;
import com.resumerag.analysis.evidence.RequirementMatchingService;
import com.resumerag.analysis.evidence.ResumeEvidenceRetrievalService;
import com.resumerag.analysis.evidence.ResumeProfile;
import com.resumerag.analysis.evidence.ResumeProfileService;
import com.resumerag.analysis.extraction.JobDescriptionExtractionService;
import com.resumerag.analysis.extraction.JobDescriptionProfile;
import com.resumerag.analysis.model.AnalysisNarrative;
import com.resumerag.analysis.model.EvidenceCandidate;
import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.ProjectAssessment;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoredAnalysis;
import com.resumerag.analysis.scoring.AnalysisRecommendationService;
import com.resumerag.analysis.scoring.AnalysisScoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The whole analysis, in one deterministic pass.
 *
 * <p>Replaces a pipeline in which the model returned the score. The order is the
 * design:
 *
 * <pre>
 *   job description  ->  classified, weighted requirements
 *   resume           ->  section-tagged evidence lines
 *   both             ->  evidence per requirement, with a strength 0-4
 *                     ->  a match state, a confidence, and the sentence used
 *                     ->  weighted category scores, null where unassessable
 *                     ->  one overall score, reproducible
 *   model            ->  a summary sentence, last, and never the score
 * </pre>
 *
 * <p>Nothing in the score path calls a model. That is what makes a result
 * reproducible, testable, and arguable - and it means a slow, cold or missing
 * local LLM can no longer change or block a verdict.
 *
 * <p>Stateless and safe to call concurrently.
 */
@Service
public class AnalysisEngine {

    private static final Logger log = LoggerFactory.getLogger(AnalysisEngine.class);

    private final JobDescriptionExtractionService jobDescriptionExtractionService;
    private final ResumeProfileService resumeProfileService;
    private final ResumeEvidenceRetrievalService evidenceRetrievalService;
    private final RequirementMatchingService matchingService;
    private final AnalysisScoringService scoringService;
    private final AnalysisRecommendationService recommendationService;
    private final ProjectRelevanceService projectRelevanceService;
    private final EducationAssessmentService educationAssessmentService;
    private final AtsQualityService atsQualityService;

    public AnalysisEngine(JobDescriptionExtractionService jobDescriptionExtractionService,
                          ResumeProfileService resumeProfileService,
                          ResumeEvidenceRetrievalService evidenceRetrievalService,
                          RequirementMatchingService matchingService,
                          AnalysisScoringService scoringService,
                          AnalysisRecommendationService recommendationService,
                          ProjectRelevanceService projectRelevanceService,
                          EducationAssessmentService educationAssessmentService,
                          AtsQualityService atsQualityService) {
        this.jobDescriptionExtractionService = jobDescriptionExtractionService;
        this.resumeProfileService = resumeProfileService;
        this.evidenceRetrievalService = evidenceRetrievalService;
        this.matchingService = matchingService;
        this.scoringService = scoringService;
        this.recommendationService = recommendationService;
        this.projectRelevanceService = projectRelevanceService;
        this.educationAssessmentService = educationAssessmentService;
        this.atsQualityService = atsQualityService;
    }

    /**
     * Analyses one resume against one job description.
     *
     * @param resumeId used to fetch the resume's own chunk embeddings; may be
     *                 {@code null}, in which case matching is lexical only
     */
    public Result analyze(String resumeText, String jobDescriptionText, UUID resumeId) {
        JobDescriptionProfile jdProfile = jobDescriptionExtractionService.extract(jobDescriptionText);
        if (jdProfile.requirements().isEmpty()) {
            log.warn("No requirements could be extracted from this job description; returning an unscored "
                    + "result rather than inventing requirements.");
            return unscorable(jdProfile);
        }

        ResumeProfile resumeProfile = resumeProfileService.build(resumeText);

        List<List<EvidenceCandidate>> evidenceByRequirement =
                evidenceRetrievalService.evidenceForAll(resumeProfile, jdProfile.requirements(), resumeId);

        List<RequirementMatch> matches = new ArrayList<>(jdProfile.requirements().size());
        for (int i = 0; i < jdProfile.requirements().size(); i++) {
            List<EvidenceCandidate> evidence = i < evidenceByRequirement.size()
                    ? evidenceByRequirement.get(i)
                    : List.of();
            matches.add(matchingService.match(jdProfile.requirements().get(i), evidence));
        }

        ExperienceAlignment experience =
                matchingService.alignExperience(resumeProfile, jdProfile.experienceRequirement());

        // The experience requirement also appears in the requirement list, so it
        // gets a match like everything else; the numeric alignment is the
        // authoritative signal and the match exists so the report can show it
        // alongside the other requirements.
        applyExperienceMatch(matches, experience);

        // --- The three dimensions that are not requirement matches. Each one is
        // asked "is there anything here to measure?" first, and returns nothing
        // when the answer is no. A resume that describes no projects does not get
        // a zero for projects, and a job description that asks for no degree does
        // not get an education score - both would be a fabricated number.
        List<ProjectAssessment> projects = projectRelevanceService.assess(resumeProfile, jdProfile.requirements());
        EducationAssessmentService.Assessment education =
                educationAssessmentService.assess(resumeProfile, jdProfile.requirements());
        AtsQualityService.Assessment ats =
                atsQualityService.assess(resumeText, resumeProfile, jdProfile.requirements());

        ScoredAnalysis scored = scoringService.score(matches, experience, jdProfile.requirements(),
                projects, education, ats);

        logMatches(matches);
        log.info("Analysis complete: overall={} ({}) required={} responsibilities={} preferred={} "
                        + "experience={}",
                scored.overallScore(), scored.matchLabel().displayName(),
                scoreOf(scored, com.resumerag.analysis.model.ScoreCategory.REQUIRED_SKILLS),
                scoreOf(scored, com.resumerag.analysis.model.ScoreCategory.RESPONSIBILITIES),
                scoreOf(scored, com.resumerag.analysis.model.ScoreCategory.PREFERRED_SKILLS),
                scoreOf(scored, com.resumerag.analysis.model.ScoreCategory.EXPERIENCE));

        return new Result(scored,
                recommendationService.strengths(matches),
                recommendationService.gaps(matches),
                recommendationService.recommendations(scored));
    }

    /**
     * Records the experience requirement's outcome on its match.
     *
     * <p>Kept in step with the numeric alignment rather than re-derived from
     * evidence text, so the requirement list and the experience axis can never
     * disagree with each other on the same question.
     */
    private void applyExperienceMatch(List<RequirementMatch> matches, ExperienceAlignment experience) {
        for (int i = 0; i < matches.size(); i++) {
            RequirementMatch match = matches.get(i);
            if (match.category() != RequirementCategory.EXPERIENCE) {
                continue;
            }
            MatchStatus status = switch (experience.status()) {
                case WITHIN_RANGE, ABOVE_RANGE -> MatchStatus.EXPLICIT_MATCH;
                case BELOW_RANGE -> MatchStatus.PARTIAL_MATCH;
                case NOT_SPECIFIED, CANDIDATE_UNKNOWN -> MatchStatus.NOT_EXPLICITLY_MENTIONED;
            };
            double confidence = switch (status) {
                case EXPLICIT_MATCH -> 1.0;
                case PARTIAL_MATCH -> 0.6;
                default -> 0.0;
            };
            matches.set(i, new RequirementMatch(
                    match.requirementIndex(), match.requirement(), match.normalizedRequirement(),
                    match.category(), match.importance(), status, confidence,
                    status == MatchStatus.EXPLICIT_MATCH ? 4 : status == MatchStatus.PARTIAL_MATCH ? 1 : 0,
                    com.resumerag.analysis.model.MatchProvenance.EXPLICIT,
                    match.resumeEvidence(), match.jdEvidence(), experience.explanation()));
        }
    }

    /**
     * Summary statistics for the log, without any resume content.
     *
     * <p>Counts only. A resume is personal data and a job description is
     * commercially sensitive, so neither appears in a log line - the counts are
     * enough to tell whether extraction and matching behaved, which is what a log
     * is for.
     */
    private void logMatches(List<RequirementMatch> matches) {
        if (!log.isDebugEnabled()) {
            return;
        }
        long explicit = matches.stream().filter(m -> m.status() == MatchStatus.EXPLICIT_MATCH).count();
        long contextual = matches.stream().filter(m -> m.status() == MatchStatus.STRONG_CONTEXTUAL_MATCH).count();
        long partial = matches.stream().filter(m -> m.status() == MatchStatus.PARTIAL_MATCH).count();
        long notMentioned = matches.stream().filter(m -> m.isUnsatisfied()).count();
        long withEvidence = matches.stream().filter(m -> !m.resumeEvidence().isEmpty()).count();
        log.debug("Requirements analysed: {} (with evidence: {})", matches.size(), withEvidence);
        log.debug("Explicit matches: {} | strong contextual: {} | partial: {} | not explicitly mentioned: {}",
                explicit, contextual, partial, notMentioned);
    }

    private String scoreOf(ScoredAnalysis scored, com.resumerag.analysis.model.ScoreCategory category) {
        com.resumerag.analysis.model.CategoryScore score = scored.category(category);
        return score == null || score.score() == null ? "n/a" : String.valueOf(score.score());
    }

    /**
     * A completed analysis.
     *
     * <p>Holds the deterministic result and the derived lists. The narrative is
     * attached separately, because it comes from a model and is allowed to fail
     * without affecting anything above it.
     */
    public record Result(
            ScoredAnalysis scored,
            List<String> strengths,
            List<String> gaps,
            List<String> recommendations
    ) {
        public Result {
            strengths = strengths == null ? List.of() : List.copyOf(strengths);
            gaps = gaps == null ? List.of() : List.copyOf(gaps);
            recommendations = recommendations == null ? List.of() : List.copyOf(recommendations);
        }

        /** The deterministic fallback narrative, used when the model is unavailable. */
        public AnalysisNarrative toNarrative() {
            return new AnalysisNarrative(
                    defaultSummary(), strengths, gaps);
        }

        /**
         * A summary built from the evidence rather than from a model.
         *
         * <p>Used as the narrative when the LLM is unreachable, and as the
         * fallback if it returns nothing usable. Written from the scored numbers
         * so it can never disagree with them - which is exactly the failure mode
         * that made the previous pipeline's model-authored summary a liability.
         */
        private String defaultSummary() {
            ScoredAnalysis s = scored;
            StringBuilder sb = new StringBuilder();
            sb.append("Overall match ")
              .append(s.overallScore()).append("% (").append(s.matchLabel().displayName()).append("). ");
            appendCategory(sb, s, com.resumerag.analysis.model.ScoreCategory.REQUIRED_SKILLS, "required skills");
            appendCategory(sb, s, com.resumerag.analysis.model.ScoreCategory.RESPONSIBILITIES, "responsibilities");
            appendCategory(sb, s, com.resumerag.analysis.model.ScoreCategory.PREFERRED_SKILLS, "preferred skills");
            if (s.experienceAlignment() != null && s.experienceAlignment().score() != null) {
                sb.append("Experience: ").append(s.experienceAlignment().explanation());
            }
            return sb.toString().trim();
        }

        private void appendCategory(StringBuilder sb, ScoredAnalysis s,
                                    com.resumerag.analysis.model.ScoreCategory category, String label) {
            com.resumerag.analysis.model.CategoryScore score = s.category(category);
            if (score == null || score.score() == null) {
                return;
            }
            sb.append(label).append(' ').append(format(score.score())).append("%. ");
        }

        private String format(double value) {
            return String.valueOf(Math.round(value * 10) / 10.0);
        }
    }

    /**
     * A result for a job description we could not read any requirements from.
     *
     * <p>Reported with a score of zero and a label of "not scored", and the
     * summary saying so explicitly. A score is still required because the existing
     * API contract carries an integer, but a user must never be shown "0% match"
     * as a verdict on a job description the engine simply failed to parse - that
     * reads as a rejection of the candidate for a parsing failure.
     */
    static Result unscorable(JobDescriptionProfile profile) {
        return new Result(
                new ScoredAnalysis(0, com.resumerag.analysis.model.MatchLabel.WEAK_MATCH, List.of(),
                        ExperienceAlignment.unknown(), List.of(), profile.requirements(), 0.0, 0,
                        new ScoredAnalysis.AssessorDetail(null, null, null)),
                List.of(), List.of(),
                List.of("This job description could not be broken into specific requirements, so no match "
                        + "score was calculated. Paste the full description, including its required and "
                        + "preferred skills sections."));
    }
}
