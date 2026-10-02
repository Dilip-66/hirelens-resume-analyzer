package com.resumerag.generation;

import com.resumerag.analysis.model.AnalysisNarrative;
import com.resumerag.analysis.model.MatchProvenance;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.RequirementMatch;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The model writes prose; it does not decide anything.
 *
 * <p>These tests exist because of what a 7B model actually did on the real
 * validation case: asked for strengths and gaps, it quoted a job description line
 * back as something the candidate had done, and listed "Participate in code
 * reviews" as a gap without saying the resume was silent about it. Prompting
 * against that behaviour was already in place and did not work, so the claims are
 * validated against the engine's own findings instead.
 */
class NarrativeValidatorTest {

    private final NarrativeValidator validator = new NarrativeValidator();

    @Test
    @DisplayName("A strength naming a matched requirement is kept")
    void aStrengthForAMatchedRequirementIsKept() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary", List.of("Strong Java background"), List.of()),
                List.of(matched("Java"), notMet("AI/ML")),
                List.of("Java (demonstrated in professional work)"), List.of());

        assertEquals(List.of("Strong Java background"), validated.strengths());
    }

    @Test
    @DisplayName("An invented strength is dropped, not passed to the user")
    void anInventedStrengthIsDropped() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary",
                        List.of("Extensive Kubernetes production experience", "Strong Java background"),
                        List.of()),
                List.of(matched("Java"), notMet("AI/ML")),
                List.of("Java (demonstrated in professional work)"), List.of());

        // The candidate has no Kubernetes requirement and no Kubernetes evidence.
        // A model that writes it anyway has produced a claim about a person out of
        // nothing, which is the one thing a report like this must never do.
        assertFalse(validated.strengths().stream().anyMatch(s -> s.toLowerCase().contains("kubernetes")),
                "an invented strength must not reach the report, got: " + validated.strengths());
        assertTrue(validated.strengths().contains("Strong Java background"));
    }

    @Test
    @DisplayName("A strength the engine did not match is dropped")
    void aStrengthForAnUnmatchedRequirementIsDropped() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary", List.of("Deep AI/ML background"), List.of()),
                List.of(matched("Java"), notMet("AI/ML")),
                List.of("Java (demonstrated in professional work)"), List.of());

        assertFalse(validated.strengths().stream().anyMatch(s -> s.contains("AI/ML")));
    }

    @Test
    @DisplayName("A gap is rewritten to say the resume is silent, not that the candidate lacks it")
    void aGapIsRewrittenToReportSilence() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary", List.of(),
                        List.of("Participate in code reviews", "Basic knowledge of AI/ML")),
                List.of(matched("Java"), notMet("Code reviews"), notMet("AI/ML")),
                List.of(), List.of("Code reviews - not explicitly mentioned in the resume"));

        for (String gap : validated.gaps()) {
            assertTrue(gap.contains("not explicitly mentioned"),
                    "a gap must be phrased as an observation about the resume, got: " + gap);
            assertFalse(gap.toLowerCase().contains("cannot"),
                    "the engine has no evidence the candidate cannot do this, got: " + gap);
        }
        assertTrue(validated.gaps().stream().anyMatch(g -> g.startsWith("Code reviews")));
        assertTrue(validated.gaps().stream().anyMatch(g -> g.startsWith("AI/ML")));
    }

    @Test
    @DisplayName("A gap with no matching finding is dropped")
    void aGapWithNoFindingIsDropped() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary", List.of(), List.of("No Kubernetes experience whatsoever")),
                List.of(matched("Java"), notMet("AI/ML")),
                List.of(), List.of("AI/ML - not explicitly mentioned in the resume"));

        assertFalse(validated.gaps().stream().anyMatch(g -> g.contains("Kubernetes")),
                "the engine found no Kubernetes requirement, so a Kubernetes gap is a claim with nothing "
                        + "behind it");
    }

    @Test
    @DisplayName("A model that returns nothing usable falls back to the engine's lists")
    void anUnusableNarrativeFallsBack() {
        List<String> fallbackStrengths = List.of("Java (demonstrated in professional work)");
        List<String> fallbackGaps = List.of("AI/ML - not explicitly mentioned in the resume");

        AnalysisNarrative empty = validator.validate(
                new AnalysisNarrative("summary", List.of(), List.of()),
                List.of(matched("Java"), notMet("AI/ML")), fallbackStrengths, fallbackGaps);
        assertEquals(fallbackStrengths, empty.strengths());
        assertEquals(fallbackGaps, empty.gaps());

        AnalysisNarrative nulls = validator.validate(
                new AnalysisNarrative("summary", null, null),
                List.of(matched("Java"), notMet("AI/ML")), fallbackStrengths, fallbackGaps);
        assertEquals(fallbackStrengths, nulls.strengths());
    }

    @Test
    @DisplayName("The model's summary is kept as written")
    void theSummaryIsKept() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("A well-rounded candidate with a solid backend stack.", List.of(), List.of()),
                List.of(matched("Java")), List.of("x"), List.of());

        assertEquals("A well-rounded candidate with a solid backend stack.", validated.summary());
    }

    @Test
    @DisplayName("Requirement names are matched on token boundaries")
    void namesAreMatchedOnTokenBoundaries() {
        // "Going" must not be read as the model naming a requirement called "Go".
        // A short requirement name inside an ordinary English word is the same
        // class of mistake the evidence layer avoids when it matches skills.
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary", List.of("Going to mention this eventually"), List.of()),
                List.of(notMet("Go"), notMet("Kubernetes")),
                List.of("fallback"), List.of());

        assertEquals(List.of("fallback"), validated.strengths(),
                "a coincidence of substring must not be read as the model naming the requirement");
    }

    @Test
    @DisplayName("A requirement genuinely named in the sentence is still recognised")
    void aGenuineNameIsStillRecognised() {
        AnalysisNarrative validated = validator.validate(
                new AnalysisNarrative("summary", List.of("Going from a strong Java foundation"), List.of()),
                List.of(matched("Java")),
                List.of("fallback"), List.of());

        assertEquals(List.of("Going from a strong Java foundation"), validated.strengths());
    }

    private static RequirementMatch matched(String name) {
        return match(name, MatchStatus.EXPLICIT_MATCH, 1.0, 4);
    }

    private static RequirementMatch notMet(String name) {
        return match(name, MatchStatus.NOT_EXPLICITLY_MENTIONED, 0.0, 0);
    }

    private static RequirementMatch match(String name, MatchStatus status, double confidence, int strength) {
        return new RequirementMatch(0, name, name.toUpperCase(), RequirementCategory.REQUIRED_SKILL,
                RequirementImportance.HIGH, status, confidence, strength, MatchProvenance.EXPLICIT,
                status == MatchStatus.NOT_EXPLICITLY_MENTIONED ? List.of() : List.of("evidence"),
                List.of("jd line"), "because");
    }
}
