package com.resumerag.analysis.scoring;

import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.ScoreCategory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every tunable number the scoring engine uses, in one place.
 *
 * <p>The whole point is that no weight, credit or threshold is written into a
 * scoring method. A score that is produced by constants scattered through the code
 * cannot be argued with, tuned without a deploy, or reviewed in one diff - and a
 * screening score is exactly the kind of number a candidate will want to see
 * justified.
 *
 * <p>Bound from {@code app.scoring.*} so the balance can be changed per
 * environment without touching Java, and so the values that produced a given
 * score are readable from configuration rather than reconstructed from a diff.
 */
@Component
@ConfigurationProperties(prefix = "app.scoring")
public class ScoringConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ScoringConfiguration.class);

    private final Map<ScoreCategory, Double> weights = new LinkedHashMap<>();

    // --- Status credits -------------------------------------------------
    private double explicitMatchCredit = 1.00;
    private double strongContextualMatchCredit = 0.85;
    private double partialMatchCredit = 0.50;
    private double notExplicitlyMentionedCredit = 0.00;
    private double conflictCredit = 0.00;

    // --- Importance multipliers (within a category) ---------------------
    private double criticalImportanceWeight = 1.50;
    private double highImportanceWeight = 1.00;
    private double mediumImportanceWeight = 0.75;
    private double lowImportanceWeight = 0.50;

    // --- Confidence -----------------------------------------------------
    /**
     * Floor applied before a confidence scales a match's credit.
     *
     * <p>Without it, a weak-but-real signal rounds to nearly nothing and
     * "PARTIAL_MATCH" becomes indistinguishable from "absent" - which is the
     * flat binary the four-state model exists to replace.
     */
    private double confidenceFloor = 0.50;
    private double confidenceCeiling = 1.00;

    /**
     * Confidence for a requirement used in professional work, at full certainty.
     *
     * <p>1.0, not 0.95. When the resume names the technology in a role, the
     * classification is not a judgement call, and discounting it would put a hard
     * ceiling under a flawless candidate - a candidate who meets every requirement
     * with professional evidence would never read 100%, which reads as a defect
     * rather than as caution. The confidence factor exists to damp
     * <em>uncertain</em> classifications, not to tax certain ones.
     */
    private double explicitMatchConfidence = 1.00;

    /** Lower, because naming a skill in a list is a weaker claim than using it. */
    private double listedSkillConfidence = 0.85;
    private double strongContextualConfidence = 0.70;
    private double partialMatchConfidence = 0.45;
    private double conflictConfidence = 0.10;

    /**
     * How much a compound requirement scores when only some of its terms are
     * evidenced. A half-satisfied "Git and GitHub" is not half a match: the
     * evidence that exists is still evidence.
     */
    private double compoundPartialFloor = 0.50;

    // --- Evidence thresholds -------------------------------------------
    /** Similarity at or above which a semantic hit counts as contextual evidence. */
    private double semanticStrongThreshold = 0.72;
    /** Below this, a passage is not evidence and is discarded. */
    private double semanticWeakThreshold = 0.60;
    private int semanticHitLimit = 3;
    private int maxEvidencePerRequirement = 3;

    // --- Experience alignment ------------------------------------------
    /**
     * How hard a shortfall below the stated minimum is scored.
     *
     * <p>Full proportionality. One year against a three-year minimum is a third
     * of the experience, and rounding that up to "close enough" is precisely the
     * kind of softening that makes a screening score indefensible.
     */
    private double experienceShortfallExponent = 1.0;

    /**
     * Ceiling for an experience figure derived from employment dates.
     *
     * <p>Below 100 by design. Dates are the document's own arithmetic rather than
     * the candidate's statement, so the dimension is informative but not
     * authoritative - and a shortfall computed from inferred dates should be
     * treated as a signal to read the CV, not as a verdict.
     */
    private double inferredExperienceCeiling = 90.0;

    // --- Projects -------------------------------------------------------
    // Within the projects dimension. Technology dominates because building with
    // the stack is the point of a project; the deliverable check is a small
    // correction for work that produced nothing observable.
    private double projectTechnologyWeight = 0.50;
    private double projectResponsibilityWeight = 0.30;
    private double projectOutcomeWeight = 0.20;

    // --- ATS -----------------------------------------------------------
    // Within the ATS dimension. Every one of these is measurable from extracted
    // text; none of them is a claim about layout or typography, which the backend
    // cannot see.
    private double atsSectionWeight = 0.25;
    private double atsContactWeight = 0.15;
    private double atsExtractionWeight = 0.20;
    private double atsCoverageWeight = 0.20;
    private double atsDuplicationWeight = 0.08;
    private double atsStuffingWeight = 0.07;
    private double atsStructureWeight = 0.05;

    // --- Output ---------------------------------------------------------
    private List<LabelBand> labelBands = defaultLabelBands();
    private double roundingDecimals = 1.0;

    public ScoringConfiguration() {
        for (ScoreCategory category : ScoreCategory.values()) {
            weights.put(category, category.defaultWeight());
        }
    }

    /**
     * Fails fast on a configuration that cannot produce a meaningful score.
     *
     * <p>Called once at startup. A weight set summing past 1.0 would silently
     * rescale every score; a negative credit would invert the ranking. Both are
     * far better caught here than explained to a user.
     */
    public void validate() {
        double total = 0.0;
        for (Map.Entry<ScoreCategory, Double> entry : weights.entrySet()) {
            Double weight = entry.getValue();
            if (weight == null || weight < 0) {
                throw new IllegalStateException(
                        "app.scoring: weight for " + entry.getKey() + " must be zero or positive, got " + weight);
            }
            total += weight;
        }
        if (total > 1.0001) {
            throw new IllegalStateException(
                    "app.scoring: category weights sum to " + total + ", which exceeds 1.0. The overall score "
                            + "is a weighted mean over assessed categories, so weights above 1.0 are a "
                            + "configuration error rather than something to rescale silently.");
        }
        if (confidenceFloor <= 0 || confidenceCeiling < confidenceFloor || confidenceCeiling > 1) {
            throw new IllegalStateException(
                    "app.scoring: confidence bounds must satisfy 0 < floor <= ceiling <= 1, got floor="
                            + confidenceFloor + " ceiling=" + confidenceCeiling);
        }
        if (semanticWeakThreshold > semanticStrongThreshold) {
            throw new IllegalStateException(
                    "app.scoring: semanticWeakThreshold must not exceed semanticStrongThreshold, got "
                            + semanticWeakThreshold + " and " + semanticStrongThreshold);
        }
        for (MatchStatus status : MatchStatus.values()) {
            double credit = creditFor(status);
            if (credit < 0 || credit > 1) {
                throw new IllegalStateException(
                        "app.scoring: the credit for " + status + " must be between 0 and 1, got " + credit
                                + ". A credit outside that range would either invert the ranking or exceed a "
                                + "perfect match.");
            }
        }
        for (double importanceWeight : List.of(criticalImportanceWeight, highImportanceWeight,
                mediumImportanceWeight, lowImportanceWeight)) {
            if (importanceWeight < 0) {
                throw new IllegalStateException(
                        "app.scoring: importance weights must be zero or positive, got " + importanceWeight);
            }
        }
        if (maxEvidencePerRequirement < 1) {
            throw new IllegalStateException(
                    "app.scoring: maxEvidencePerRequirement must be at least 1, otherwise a matched "
                            + "requirement would be shown with no evidence to justify it");
        }
        List<LabelBand> bands = new ArrayList<>(labelBands);
        bands.sort((a, b) -> Double.compare(b.minimumScore(), a.minimumScore()));
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).minimumScore() < 0) {
                // Zero is legitimate: the lowest band is the catch-all that every
                // score below the next band up falls into, and without it a very
                // poor match would have no label at all.
                throw new IllegalStateException(
                        "app.scoring: label band minimumScores must be zero or positive, got "
                                + bands.get(i).minimumScore() + " for " + bands.get(i).label());
            }
            if (i > 0 && bands.get(i).minimumScore() >= bands.get(i - 1).minimumScore()) {
                throw new IllegalStateException("app.scoring: label bands must have distinct minimumScores");
            }
        }
        if (log.isInfoEnabled()) {
            log.info("Scoring weights: {}", weights);
        }
    }

    // ---------------------------------------------------------------------
    // Accessors
    // ---------------------------------------------------------------------

    /** The credit a match status earns, before confidence is applied. */
    public double creditFor(MatchStatus status) {
        return switch (status) {
            case EXPLICIT_MATCH -> explicitMatchCredit;
            case STRONG_CONTEXTUAL_MATCH -> strongContextualMatchCredit;
            case PARTIAL_MATCH -> partialMatchCredit;
            case NOT_EXPLICITLY_MENTIONED -> notExplicitlyMentionedCredit;
            case CONFLICT -> conflictCredit;
        };
    }

    /** The confidence the classifier assigns to a status, before adjustment. */
    public double confidenceFor(MatchStatus status) {
        return switch (status) {
            case EXPLICIT_MATCH -> explicitMatchConfidence;
            case STRONG_CONTEXTUAL_MATCH -> strongContextualConfidence;
            case PARTIAL_MATCH -> partialMatchConfidence;
            case NOT_EXPLICITLY_MENTIONED -> 0.0;
            case CONFLICT -> conflictConfidence;
        };
    }

    public double weightFor(ScoreCategory category) {
        return weights.getOrDefault(category, category.defaultWeight());
    }

    /**
     * Per-category weight setters, one per dimension.
     *
     * <p>Written out rather than relying on a {@code Map<ScoreCategory, Double>}
     * binding, because relaxed binding cannot map a YAML key onto an enum-keyed
     * map entry - which would leave the whole block silently unbound and every
     * weight sitting at its default while the configuration file claimed
     * otherwise.
     */
    public void setRequiredSkillsWeight(double weight) { weights.put(ScoreCategory.REQUIRED_SKILLS, weight); }
    public void setExperienceWeight(double weight) { weights.put(ScoreCategory.EXPERIENCE, weight); }
    public void setResponsibilitiesWeight(double weight) { weights.put(ScoreCategory.RESPONSIBILITIES, weight); }
    public void setPreferredSkillsWeight(double weight) { weights.put(ScoreCategory.PREFERRED_SKILLS, weight); }
    public void setProjectsWeight(double weight) { weights.put(ScoreCategory.PROJECTS, weight); }
    public void setEducationWeight(double weight) { weights.put(ScoreCategory.EDUCATION, weight); }
    public void setAtsWeight(double weight) { weights.put(ScoreCategory.ATS, weight); }

    /** Programmatic override, for tests and experiments. */
    public void setWeight(ScoreCategory category, double weight) {
        weights.put(category, weight);
    }

    public Map<ScoreCategory, Double> getWeights() {
        return Map.copyOf(weights);
    }

    /** The band a score falls into, lowest band first. */
    public com.resumerag.analysis.model.MatchLabel labelFor(double score) {
        List<LabelBand> bands = new ArrayList<>(labelBands);
        bands.sort((a, b) -> Double.compare(b.minimumScore(), a.minimumScore()));
        for (LabelBand band : bands) {
            if (score >= band.minimumScore()) {
                return band.label();
            }
        }
        return bands.isEmpty()
                ? com.resumerag.analysis.model.MatchLabel.WEAK_MATCH
                : bands.get(bands.size() - 1).label();
    }

    /**
     * Deliberately conservative bands.
     *
     * <p>A score only reaches "Excellent" near the top of the scale, because a
     * screening verdict read as "excellent" invites someone to act on it, and one
     * produced by a resume that never mentions code review should not get there.
     */
    private static List<LabelBand> defaultLabelBands() {
        return List.of(
                new LabelBand(90.0, com.resumerag.analysis.model.MatchLabel.EXCELLENT_MATCH),
                new LabelBand(75.0, com.resumerag.analysis.model.MatchLabel.STRONG_MATCH),
                new LabelBand(60.0, com.resumerag.analysis.model.MatchLabel.GOOD_MATCH),
                new LabelBand(45.0, com.resumerag.analysis.model.MatchLabel.MODERATE_MATCH),
                new LabelBand(0.0, com.resumerag.analysis.model.MatchLabel.WEAK_MATCH));
    }

    /** A score threshold and the plain-language band it opens. */
    public record LabelBand(double minimumScore, com.resumerag.analysis.model.MatchLabel label) {}

    public double getExplicitMatchCredit() { return explicitMatchCredit; }
    public void setExplicitMatchCredit(double v) { this.explicitMatchCredit = v; }
    public double getStrongContextualMatchCredit() { return strongContextualMatchCredit; }
    public void setStrongContextualMatchCredit(double v) { this.strongContextualMatchCredit = v; }
    public double getPartialMatchCredit() { return partialMatchCredit; }
    public void setPartialMatchCredit(double v) { this.partialMatchCredit = v; }
    public double getNotExplicitlyMentionedCredit() { return notExplicitlyMentionedCredit; }
    public void setNotExplicitlyMentionedCredit(double v) { this.notExplicitlyMentionedCredit = v; }
    public double getConflictCredit() { return conflictCredit; }
    public void setConflictCredit(double v) { this.conflictCredit = v; }

    public double getCriticalImportanceWeight() { return criticalImportanceWeight; }
    public void setCriticalImportanceWeight(double v) { this.criticalImportanceWeight = v; }
    public double getHighImportanceWeight() { return highImportanceWeight; }
    public void setHighImportanceWeight(double v) { this.highImportanceWeight = v; }
    public double getMediumImportanceWeight() { return mediumImportanceWeight; }
    public void setMediumImportanceWeight(double v) { this.mediumImportanceWeight = v; }
    public double getLowImportanceWeight() { return lowImportanceWeight; }
    public void setLowImportanceWeight(double v) { this.lowImportanceWeight = v; }

    public double getConfidenceFloor() { return confidenceFloor; }
    public void setConfidenceFloor(double v) { this.confidenceFloor = v; }
    public double getConfidenceCeiling() { return confidenceCeiling; }
    public void setConfidenceCeiling(double v) { this.confidenceCeiling = v; }
    public double getExplicitMatchConfidence() { return explicitMatchConfidence; }
    public void setExplicitMatchConfidence(double v) { this.explicitMatchConfidence = v; }
    public double getListedSkillConfidence() { return listedSkillConfidence; }
    public void setListedSkillConfidence(double v) { this.listedSkillConfidence = v; }
    public double getStrongContextualConfidence() { return strongContextualConfidence; }
    public void setStrongContextualConfidence(double v) { this.strongContextualConfidence = v; }
    public double getPartialMatchConfidence() { return partialMatchConfidence; }
    public void setPartialMatchConfidence(double v) { this.partialMatchConfidence = v; }
    public double getConflictConfidence() { return conflictConfidence; }
    public void setConflictConfidence(double v) { this.conflictConfidence = v; }

    public double getCompoundPartialFloor() { return compoundPartialFloor; }
    public void setCompoundPartialFloor(double v) { this.compoundPartialFloor = v; }

    public double getSemanticStrongThreshold() { return semanticStrongThreshold; }
    public void setSemanticStrongThreshold(double v) { this.semanticStrongThreshold = v; }
    public double getSemanticWeakThreshold() { return semanticWeakThreshold; }
    public void setSemanticWeakThreshold(double v) { this.semanticWeakThreshold = v; }
    public int getSemanticHitLimit() { return semanticHitLimit; }
    public void setSemanticHitLimit(int v) { this.semanticHitLimit = v; }
    public int getMaxEvidencePerRequirement() { return maxEvidencePerRequirement; }
    public void setMaxEvidencePerRequirement(int v) { this.maxEvidencePerRequirement = v; }

    public double getExperienceShortfallExponent() { return experienceShortfallExponent; }
    public void setExperienceShortfallExponent(double v) { this.experienceShortfallExponent = v; }
    public double getInferredExperienceCeiling() { return inferredExperienceCeiling; }
    public void setInferredExperienceCeiling(double v) { this.inferredExperienceCeiling = v; }

    public double getProjectTechnologyWeight() { return projectTechnologyWeight; }
    public void setProjectTechnologyWeight(double v) { this.projectTechnologyWeight = v; }
    public double getProjectResponsibilityWeight() { return projectResponsibilityWeight; }
    public void setProjectResponsibilityWeight(double v) { this.projectResponsibilityWeight = v; }
    public double getProjectOutcomeWeight() { return projectOutcomeWeight; }
    public void setProjectOutcomeWeight(double v) { this.projectOutcomeWeight = v; }

    public double getAtsSectionWeight() { return atsSectionWeight; }
    public void setAtsSectionWeight(double v) { this.atsSectionWeight = v; }
    public double getAtsContactWeight() { return atsContactWeight; }
    public void setAtsContactWeight(double v) { this.atsContactWeight = v; }
    public double getAtsExtractionWeight() { return atsExtractionWeight; }
    public void setAtsExtractionWeight(double v) { this.atsExtractionWeight = v; }
    public double getAtsCoverageWeight() { return atsCoverageWeight; }
    public void setAtsCoverageWeight(double v) { this.atsCoverageWeight = v; }
    public double getAtsDuplicationWeight() { return atsDuplicationWeight; }
    public void setAtsDuplicationWeight(double v) { this.atsDuplicationWeight = v; }
    public double getAtsStuffingWeight() { return atsStuffingWeight; }
    public void setAtsStuffingWeight(double v) { this.atsStuffingWeight = v; }
    public double getAtsStructureWeight() { return atsStructureWeight; }
    public void setAtsStructureWeight(double v) { this.atsStructureWeight = v; }

    public List<LabelBand> getLabelBands() { return labelBands; }
    public void setLabelBands(List<LabelBand> labelBands) { this.labelBands = labelBands; }
    public double getRoundingDecimals() { return roundingDecimals; }
    public void setRoundingDecimals(double v) { this.roundingDecimals = v; }
}
