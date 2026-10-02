package com.resumerag.analysis;

import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.MatchLabel;
import com.resumerag.analysis.model.ScoreCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Experience years, and the difference between a stated figure and a derived one.
 *
 * <p>The benchmark found a resume whose dates implied eleven years against a role
 * asking for five, which scored it a perfect 100. That number was arithmetic over
 * the document, not a statement by the candidate, so the engine now labels it and
 * refuses to let it reach the top of the scale on its own. These tests hold both
 * halves of that decision: the estimate is still used, because dates are evidence,
 * and it is still marked as the engine's own reading rather than the candidate's
 * claim.
 */
class ExperienceCalibrationTest {

    private static final String JD = """
            Senior Backend Engineer

            Experience: 5–8 years

            Required Skills:

            * Strong programming skills in Java
            * Experience with Spring Boot

            Responsibilities:

            * Develop and maintain backend services using Java and Spring Boot.
            """;

    @Test
    @DisplayName("A figure the candidate wrote is used as stated")
    void aStatedFigureIsUsedAsStated() {
        String resume = """
                Arjun Patel
                arjun@example.com | +44 7700 900123 | London, UK

                Summary:
                Software developer with 6 years of experience building Java services.

                Experience:
                Software Engineer — Acme | 2019–Present
                * Built and maintained backend services using Java and Spring Boot.
                """;

        ExperienceAlignment alignment = analysisEngine().analyze(resume, JD, null).scored()
                .experienceAlignment();

        assertTrue(alignment.yearsWereStated(),
                "the candidate wrote '6 years of experience', so that is a stated figure");
        assertEquals(6.0, alignment.candidateYears(), 0.01);
        assertFalse(alignment.explanation().toLowerCase().contains("derived"),
                "a stated figure must not be described as derived");
    }

    @Test
    @DisplayName("Years are derived from dates when the candidate states none")
    void datesAreUsedWhenNothingIsStated() {
        String resume = """
                Arjun Patel
                arjun@example.com | +44 7700 900123 | London, UK

                Technical Skills:
                * Java, Spring Boot

                Experience:
                Software Engineer — Acme | 2019–Present
                * Built and maintained backend services using Java and Spring Boot.
                * Worked with PostgreSQL and Docker across a team of engineers for years.
                """;

        ExperienceAlignment alignment = analysisEngine().analyze(resume, JD, null).scored()
                .experienceAlignment();

        assertFalse(alignment.yearsWereStated(),
                "nothing in this resume states a figure, so the number is the engine's reading");
        assertTrue(alignment.candidateYears() != null && alignment.candidateYears() > 4,
                "the dates are still evidence: " + alignment.candidateYears());
    }

    @Test
    @DisplayName("An inferred figure cannot score a candidate into a perfect match on its own")
    void anInferredFigureIsCapped() {
        String veryLongCareer = """
                Arjun Patel
                arjun@example.com | +44 7700 900123 | London, UK

                Technical Skills:
                * Java, Spring Boot

                Experience:
                Software Engineer — Acme | 2001–Present
                * Built and maintained backend services using Java and Spring Boot.
                * Worked with PostgreSQL and Docker across a team of engineers for years.
                """;

        AnalysisEngine.Result result = analysisEngine().analyze(veryLongCareer, JD, null);
        double experience = result.scored().category(ScoreCategory.EXPERIENCE).score();

        assertTrue(experience <= 90.0,
                "dates can be mistyped and an open-ended range runs to today, so an inferred figure is "
                        + "not allowed to reach 100 on its own: " + experience);
    }

    @Test
    @DisplayName("The reference pair's numbers do not move")
    void theReferencePairIsStable() {
        var result = analysisEngine().analyze(AnalysisEngineFixture.ARJUN_RESUME,
                AnalysisEngineFixture.FULL_STACK_JD, null);

        assertEquals(90, result.scored().overallScore(),
                "this pair is the calibration reference; if it moves, every scenario's number is "
                        + "suspect and the change has to be justified in CALIBRATION.md");
        assertEquals(MatchLabel.EXCELLENT_MATCH, result.scored().matchLabel());
        assertEquals(89.9, result.scored().category(ScoreCategory.REQUIRED_SKILLS).score(), 0.05);
        assertEquals(100.0, result.scored().category(ScoreCategory.EXPERIENCE).score(), 0.05);
        assertEquals(81.0, result.scored().category(ScoreCategory.RESPONSIBILITIES).score(), 0.05);
        assertEquals(85.6, result.scored().category(ScoreCategory.PREFERRED_SKILLS).score(), 0.05);
    }

    @Test
    @DisplayName("Categories that were never measured stay null")
    void unmeasuredCategoriesStayNull() {
        var scored = analysisEngine().analyze(AnalysisEngineFixture.ARJUN_RESUME,
                AnalysisEngineFixture.FULL_STACK_JD, null).scored();

        assertEquals(null, scored.category(ScoreCategory.PROJECTS).score(),
                "this resume describes no projects, so there is nothing to measure");
        assertEquals(null, scored.category(ScoreCategory.EDUCATION).score(),
                "this job description asks for no degree, so there is nothing to measure");
        assertTrue(scored.category(ScoreCategory.PROJECTS).note() != null
                        && !scored.category(ScoreCategory.PROJECTS).note().isBlank(),
                "an unmeasured category has to explain itself");
    }

    private static AnalysisEngine analysisEngine() {
        return AnalysisEngineFixture.engine();
    }
}