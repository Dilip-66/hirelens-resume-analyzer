package com.resumerag.analysis.assess;

import com.resumerag.analysis.evidence.ResumeProfile;
import com.resumerag.analysis.evidence.ResumeProfileService;
import com.resumerag.analysis.extraction.ExperienceRequirementParser;
import com.resumerag.analysis.extraction.JobDescriptionExtractionService;
import com.resumerag.analysis.extraction.RequirementNormalizationService;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.scoring.ScoringConfiguration;
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
 * What the ATS check is allowed to claim.
 *
 * <p>This engine reads text. It never sees the PDF, so it cannot know whether a
 * resume uses two columns, embeds an icon as a phone number, or picks a font
 * that breaks a parser. The tests here exist mostly to hold that line: the score
 * must be derived from the text, and it must say so, because a resume-scoring
 * product that silently implied otherwise would be claiming knowledge it does not
 * have.
 */
class AtsQualityServiceTest {

    private final ResumeProfileService profileService =
            new ResumeProfileService(new ExperienceRequirementParser(Clock.systemUTC()));
    private final AtsQualityService service = new AtsQualityService(configuration());

    private static ScoringConfiguration configuration() {
        ScoringConfiguration configuration = new ScoringConfiguration();
        configuration.validate();
        return configuration;
    }

    private ResumeProfile profile(String text) {
        return profileService.build(text);
    }

    private List<Requirement> requirements(String... lines) {
        String jd = "Required Skills:\n\n"
                + String.join("\n", Arrays.stream(lines).map(line -> "* " + line).toList())
                + "\n\nResponsibilities:\n\n* Build and maintain backend services.\n";
        return new JobDescriptionExtractionService(
                new RequirementNormalizationService(),
                new ExperienceRequirementParser(Clock.systemUTC()))
                .extract(jd).requirements();
    }

    @Test
    @DisplayName("A clean, well-structured resume scores well")
    void aWellStructuredResumeScoresWell() {
        String resume = """
                Jane Roe
                jane@example.com | +44 7700 900001 | London, UK

                Summary:
                Backend engineer with five years building Java services.

                Technical Skills:
                * Java, Spring Boot, PostgreSQL, Docker

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java and Spring Boot.
                * Cut API response time by 40% by rewriting the query layer.

                Education:
                * BSc Computer Science, University of Manchester, 2020
                """;

        AtsQualityService.Assessment assessment = service.assess(resume, profile(resume),
                requirements("Strong programming skills in Java", "Experience with Spring Boot"));

        assertTrue(assessment.assessed());
        assertNotNull(assessment.score());
        assertTrue(assessment.score() >= 80,
                "nothing is wrong with this resume: " + assessment.score());
        assertFalse(assessment.factors().isEmpty(), "the factors have to be reported, not just the total");
    }

    @Test
    @DisplayName("The assessment says it measured text, and never claims layout")
    void theAssessmentNeverClaimsToHaveSeenTheLayout() {
        String resume = """
                Jane Roe
                jane@example.com | +44 7700 900001 | London, UK

                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java.
                """;

        AtsQualityService.Assessment assessment = service.assess(resume, profile(resume), requirements("Java"));

        assertTrue(assessment.note().toLowerCase().contains("text"),
                "the note must be explicit that only text was assessed: " + assessment.note());
        for (String forbidden : List.of("font", "column", "layout", "image", "graphic", "colour", "color")) {
            assertFalse(assessment.note().toLowerCase().contains(forbidden),
                    "the note must not imply the layout was inspected, and '" + forbidden + "' does: "
                            + assessment.note());
        }
        for (AtsQualityService.Factor factor : assessment.factors()) {
            assertFalse(factor.evidence().toLowerCase().contains("font")
                            || factor.evidence().toLowerCase().contains("column"),
                    "a factor's evidence is a quote from the text, so it cannot be about fonts: "
                            + factor.evidence());
        }
    }

    @Test
    @DisplayName("A resume with no contact details is flagged, and says which are missing")
    void missingContactDetailsAreCaught() {
        String resume = """
                Jane Roe

                Technical Skills:
                * Java, Spring Boot, PostgreSQL, Docker

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java and Spring Boot, working with
                  PostgreSQL and Docker across a team of engineers for several years.
                * Designed the persistence layer and reviewed the changes other engineers merged.
                * Cut API response time by 40% by rewriting the query layer and adding an index.

                Education:
                * BSc Computer Science, University of Manchester, 2020
                """;

        AtsQualityService.Assessment assessment = service.assess(resume, profile(resume), requirements("Java"));

        AtsQualityService.Factor contact = assessment.factors().stream()
                .filter(f -> f.factor().toLowerCase().contains("contact"))
                .findFirst().orElseThrow();
        assertFalse(contact.isPass(),
                "a resume with no email and no phone number cannot pass that check");
        assertTrue(contact.evidence().contains("both an email address and a phone number"),
                "the report has to name what is missing, so a reader can fix it: " + contact.evidence());
    }

    @Test
    @DisplayName("Too little text to judge is unmeasured, not scored badly")
    void tooLittleTextIsUnmeasuredRatherThanBad() {
        String resume = "Jane Roe\njane@example.com | London";

        AtsQualityService.Assessment assessment = service.assess(resume, profile(resume), requirements("Java"));

        assertFalse(assessment.assessed(), "four lines cannot support an ATS judgement");
        assertNull(assessment.score(), "and the score must be null rather than a low number");
    }

    @Test
    @DisplayName("No extracted text at all is handled without failing")
    void emptyTextIsHandled() {
        AtsQualityService.Assessment assessment = service.assess("", profile(""), requirements("Java"));

        assertFalse(assessment.assessed());
        assertNull(assessment.score());
        assertNotNull(assessment.note());
    }

    @Test
    @DisplayName("Factor weights add up to one, so the score is a weighted mean and not a sum")
    void factorWeightsSumToOne() {
        String resume = """
                Jane Roe
                jane@example.com | +44 7700 900001 | London, UK

                Summary:
                Backend engineer with five years building Java services.

                Technical Skills:
                * Java, Spring Boot, PostgreSQL, Docker

                Experience:
                Software Engineer — Acme | 2021–Present
                * Built and maintained backend services using Java and Spring Boot.
                * Cut API response time by 40% by rewriting the query layer.

                Education:
                * BSc Computer Science, University of Manchester, 2020
                """;

        double total = service.assess(resume, profile(resume), requirements("Java")).factors().stream()
                .mapToDouble(AtsQualityService.Factor::weight).sum();

        assertEquals(1.0, total, 0.0001,
                "weights that do not sum to one make the score drift as factors are added");
    }
}