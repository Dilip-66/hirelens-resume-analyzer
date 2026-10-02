package com.resumerag.analysis.scoring;

import com.resumerag.analysis.model.CategoryScore;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.MatchProvenance;
import com.resumerag.analysis.model.ScoreCategory;
import com.resumerag.analysis.model.ScoredAnalysis;
import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.RequirementMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The scoring arithmetic, tested on its own.
 *
 * <p>These pin the properties that make a score defensible rather than any
 * particular number: that the weights are the only source of a category's
 * importance, that an unmeasured dimension is excluded rather than counted as
 * zero, that missing requirements count as zero rather than being dropped from
 * the average, and that a broken configuration is rejected at startup instead of
 * producing quietly wrong percentages.
 */
class AnalysisScoringServiceTest {

    private final ScoringConfiguration configuration = configuration();
    private final AnalysisScoringService service = new AnalysisScoringService(configuration);

    private static ScoringConfiguration configuration() {
        ScoringConfiguration configuration = new ScoringConfiguration();
        configuration.validate();
        return configuration;
    }

    @Test
    @DisplayName("An unmeasured dimension is excluded, not counted as zero")
    void anUnmeasuredDimensionIsExcludedNotZeroed() {
        // Required skills perfect, experience perfect, nothing else assessable.
        ScoredAnalysis result = service.score(
                List.of(
                        match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                                MatchStatus.EXPLICIT_MATCH, 1.0, 4),
                        match(1, "Docker", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                                MatchStatus.EXPLICIT_MATCH, 1.0, 4)),
                experience(100.0),
                List.of());

        // With projects, education and ATS excluded, and the rest perfect, the
        // score is 100. Counting them as zero would report a flawless candidate as
        // a 71% match purely because the engine cannot measure ATS readiness.
        assertEquals(100, result.overallScore());
        assertNull(result.category(ScoreCategory.PROJECTS).score());
        assertNull(result.category(ScoreCategory.ATS).score());
    }

    @Test
    @DisplayName("A missing requirement counts as zero rather than being dropped")
    void missingRequirementsCountAsZero() {
        ScoredAnalysis allPresent = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                        MatchStatus.EXPLICIT_MATCH, 1.0, 4),
                        match(1, "Docker", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                                MatchStatus.EXPLICIT_MATCH, 1.0, 4)),
                experience(100.0), List.of());

        ScoredAnalysis oneMissing = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                        MatchStatus.EXPLICIT_MATCH, 1.0, 4),
                        match(1, "Docker", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                                MatchStatus.NOT_EXPLICITLY_MENTIONED, 0.0, 0)),
                experience(100.0), List.of());

        assertEquals(100, allPresent.overallScore());
        // Excluding the absent requirement would score it 100 too, and let a
        // twenty-requirement job description that a candidate happens to meet
        // outrank one they meet completely.
        assertTrue(oneMissing.overallScore() < 80,
                "an absent requirement has to cost something; got " + oneMissing.overallScore());
    }

    @Test
    @DisplayName("A critical requirement outweighs a low-importance one inside its category")
    void importanceMattersWithinACategory() {
        ScoredAnalysis bothPresent = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.CRITICAL,
                        MatchStatus.EXPLICIT_MATCH, 1.0, 4),
                        match(1, "Git", RequirementCategory.REQUIRED_SKILL, RequirementImportance.LOW,
                                MatchStatus.EXPLICIT_MATCH, 1.0, 4)),
                experience(100.0), List.of());
        assertEquals(100, bothPresent.overallScore());

        // Lose the low-importance one: barely a dent.
        ScoredAnalysis lostLow = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.CRITICAL,
                        MatchStatus.EXPLICIT_MATCH, 1.0, 4),
                        match(1, "Git", RequirementCategory.REQUIRED_SKILL, RequirementImportance.LOW,
                                MatchStatus.NOT_EXPLICITLY_MENTIONED, 0.0, 0)),
                experience(100.0), List.of());

        // Lose the critical one: a real hole.
        ScoredAnalysis lostCritical = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.CRITICAL,
                        MatchStatus.NOT_EXPLICITLY_MENTIONED, 0.0, 0),
                        match(1, "Git", RequirementCategory.REQUIRED_SKILL, RequirementImportance.LOW,
                                MatchStatus.EXPLICIT_MATCH, 1.0, 4)),
                experience(100.0), List.of());

        double highCost = 100 - lostCritical.overallScore();
        double lowCost = 100 - lostLow.overallScore();
        assertTrue(highCost > lowCost * 1.5,
                "losing a critical requirement must cost clearly more than losing a minor one: high="
                        + highCost + " low=" + lowCost);
    }

    @Test
    @DisplayName("Preferred skills are capped at their own weight")
    void preferredSkillsCannotDominate() {
        // Everything required present, every bonus skill missing.
        ScoredAnalysis requiredOnly = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.CRITICAL,
                        MatchStatus.EXPLICIT_MATCH, 1.0, 4)),
                experience(100.0), List.of());

        assertNotNull(requiredOnly.category(ScoreCategory.PREFERRED_SKILLS).note(),
                "an empty category must say so rather than reporting a fabricated number");
        assertNull(requiredOnly.category(ScoreCategory.PREFERRED_SKILLS).score());
        assertTrue(requiredOnly.overallScore() > 90,
                "meeting the whole required stack with no bonus skills is a strong result, got "
                        + requiredOnly.overallScore());
    }

    @Test
    @DisplayName("The score is clamped to 0-100")
    void theScoreIsClamped() {
        ScoredAnalysis nothing = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                        MatchStatus.NOT_EXPLICITLY_MENTIONED, 0.0, 0)),
                experience(0.0), List.of());

        assertTrue(nothing.overallScore() >= 0 && nothing.overallScore() <= 100);
    }

    @Test
    @DisplayName("A category with no requirements is not assessed")
    void anEmptyCategoryIsNotAssessed() {
        ScoredAnalysis result = service.score(
                List.of(match(0, "Java", RequirementCategory.REQUIRED_SKILL, RequirementImportance.HIGH,
                        MatchStatus.EXPLICIT_MATCH, 1.0, 4)),
                experience(100.0), List.of());

        CategoryScore responsibilities = result.category(ScoreCategory.RESPONSIBILITIES);
        assertNull(responsibilities.score());
        assertNotNull(responsibilities.note());
    }

    @Test
    @DisplayName("A weight set that exceeds 1.0 is rejected at startup")
    void anOverweightedConfigurationIsRejected() {
        ScoringConfiguration broken = new ScoringConfiguration();
        broken.setRequiredSkillsWeight(0.8);
        broken.setExperienceWeight(0.5);

        IllegalStateException failure = assertThrows(IllegalStateException.class, broken::validate);
        assertTrue(failure.getMessage().contains("exceeds 1.0"),
                "the message should name the problem, got: " + failure.getMessage());
    }

    @Test
    @DisplayName("Inverted confidence bounds are rejected at startup")
    void invertedConfidenceBoundsAreRejected() {
        ScoringConfiguration broken = new ScoringConfiguration();
        broken.setConfidenceFloor(0.9);
        broken.setConfidenceCeiling(0.5);

        assertThrows(IllegalStateException.class, broken::validate);
    }

    @Test
    @DisplayName("Semantic thresholds that contradict each other are rejected")
    void contradictoryThresholdsAreRejected() {
        ScoringConfiguration broken = new ScoringConfiguration();
        broken.setSemanticWeakThreshold(0.9);
        broken.setSemanticStrongThreshold(0.5);

        assertThrows(IllegalStateException.class, broken::validate);
    }

    @Test
    @DisplayName("A negative credit is rejected")
    void aNegativeCreditIsRejected() {
        ScoringConfiguration broken = new ScoringConfiguration();
        broken.setPartialMatchCredit(-0.5);

        assertThrows(IllegalStateException.class, broken::validate);
    }

    @Test
    @DisplayName("Label bands come from configuration, not from constants in the code")
    void labelBandsAreConfigurable() {
        ScoringConfiguration relaxed = configuration();
        relaxed.setLabelBands(List.of(
                new ScoringConfiguration.LabelBand(10.0, com.resumerag.analysis.model.MatchLabel.EXCELLENT_MATCH),
                new ScoringConfiguration.LabelBand(0.0, com.resumerag.analysis.model.MatchLabel.WEAK_MATCH)));

        assertEquals(com.resumerag.analysis.model.MatchLabel.EXCELLENT_MATCH, relaxed.labelFor(50));
        assertEquals(com.resumerag.analysis.model.MatchLabel.EXCELLENT_MATCH,
                configuration().labelFor(95),
                "the default bands should place 95 in the top band");
        assertEquals(com.resumerag.analysis.model.MatchLabel.WEAK_MATCH, configuration().labelFor(12));
    }

    private static RequirementMatch match(int index, String name, RequirementCategory category,
                                          RequirementImportance importance, MatchStatus status,
                                          double confidence, int strength) {
        return new RequirementMatch(index, name, name.toUpperCase(), category, importance, status,
                confidence, strength, MatchProvenance.EXPLICIT, List.of("evidence"), List.of("jd"),
                "because");
    }

    private static ExperienceAlignment experience(double score) {
        return new ExperienceAlignment(5.0, true, 1.0, 10.0, ExperienceAlignment.Status.WITHIN_RANGE, score, "ok");
    }
}
