package com.resumerag.analysis.assess;

import com.resumerag.analysis.evidence.ResumeProfile;
import com.resumerag.analysis.evidence.ResumeProfileService;
import com.resumerag.analysis.extraction.ExperienceRequirementParser;
import com.resumerag.analysis.extraction.JobDescriptionExtractionService;
import com.resumerag.analysis.extraction.RequirementNormalizationService;
import com.resumerag.analysis.model.Requirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How degrees are compared with what a job description asks for.
 *
 * <p>The decision this class protects is that education is only ever measured when
 * the job description states a requirement. Plenty of roles say nothing about
 * degrees, and scoring a resume down for a line the employer never wrote would
 * penalise the candidate for the employer's silence.
 */
class EducationAssessmentServiceTest {

    private final ResumeProfileService profileService =
            new ResumeProfileService(new ExperienceRequirementParser(Clock.systemUTC()));
    private final EducationAssessmentService service = new EducationAssessmentService();

    private ResumeProfile profile(String text) {
        return profileService.build(text);
    }

    private List<Requirement> requirements(String... lines) {
        String jd = "Required Skills:\n\n"
                + String.join("\n", Arrays.stream(lines).map(line -> "* " + line).toList()) + "\n";
        return new JobDescriptionExtractionService(
                new RequirementNormalizationService(),
                new ExperienceRequirementParser(Clock.systemUTC()))
                .extract(jd).requirements();
    }

    @Test
    @DisplayName("A job description that says nothing about education is not scored")
    void noEducationRequirementMeansNoEducationScore() {
        ResumeProfile profile = profile("""
                Jane Roe
                jane@example.com | +44 7700 900001 | London, UK

                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java.

                Education:
                * BSc Computer Science, University of Manchester, 2020
                """);

        EducationAssessmentService.Assessment assessment = service.assess(profile, requirements(
                "Strong programming skills in Java"));

        assertFalse(assessment.assessed(), "the employer asked for no degree, so there is nothing to measure");
        assertNull(assessment.score(), "an unmeasured category must be null, not 0");
        assertTrue(assessment.note().toLowerCase().contains("education requirement"),
                "the note has to explain the absence: " + assessment.note());
    }

    @Test
    @DisplayName("A stated degree that meets the requirement scores, and says which one")
    void metDegreeScores() {
        ResumeProfile profile = profile("""
                Jane Roe
                jane@example.com | +44 7700 900001 | London, UK

                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java.

                Education:
                * BSc Computer Science, University of Manchester, 2020
                """);

        EducationAssessmentService.Assessment assessment = service.assess(profile,
                requirements("Bachelor's degree in Computer Science"));

        assertTrue(assessment.assessed());
        assertNotNull(assessment.score());
        assertTrue(assessment.score() >= 80,
                "a stated, relevant degree that meets the bar should score near the top: " + assessment.score());
        assertEquals(1, assessment.outcomes().size());
        assertEquals(EducationAssessmentService.DegreeLevel.BACHELOR,
                assessment.outcomes().get(0).heldLevel(),
                "a BSc is a bachelor's degree");
    }

    @Test
    @DisplayName("A degree in another field is scored below one in the field asked for")
    void aRelatedButNotMatchingFieldScoresLower() {
        String resume = """
                %s

                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java.

                Education:
                * %s
                """;
        List<Requirement> jd = requirements("Bachelor's degree in Computer Science");

        double matched = service.assess(profile(resume.formatted("Jane Roe", "BSc Computer Science, 2020")), jd)
                .score();
        double wrongField = service.assess(profile(resume.formatted("Jane Roe", "BA English Literature, 2020")), jd)
                .score();

        assertTrue(matched > wrongField,
                "the engine cannot tell a candidate did the right course from the degree alone, but it can "
                        + "tell the field differs: " + matched + " vs " + wrongField);
    }

    @Test
    @DisplayName("A missing degree is scored as a shortfall, not as an error")
    void aMissingDegreeStillScores() {
        ResumeProfile profile = profile("""
                Jane Roe
                jane@example.com | +44 7700 900001 | London, UK

                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java.
                """);

        EducationAssessmentService.Assessment assessment = service.assess(profile,
                requirements("Bachelor's degree in Computer Science"));

        assertTrue(assessment.assessed(), "the employer did ask, so this one is measured");
        assertNotNull(assessment.score());
        assertTrue(assessment.score() < 50,
                "no degree against a degree requirement is a shortfall: " + assessment.score());
    }
}