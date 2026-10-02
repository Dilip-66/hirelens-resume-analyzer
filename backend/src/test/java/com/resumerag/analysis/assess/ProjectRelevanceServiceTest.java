package com.resumerag.analysis.assess;

import com.resumerag.analysis.evidence.ResumeProfile;
import com.resumerag.analysis.evidence.ResumeProfileService;
import com.resumerag.analysis.extraction.ExperienceRequirementParser;
import com.resumerag.analysis.extraction.JobDescriptionExtractionService;
import com.resumerag.analysis.extraction.RequirementNormalizationService;
import com.resumerag.analysis.model.ProjectAssessment;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a resume's projects are read and scored.
 *
 * <p>The bug that shaped this class was a section transition that swallowed the
 * first line of the projects section, so a resume whose strongest project was
 * listed first scored as if that project did not exist. Each test below names a
 * way this measurement can go wrong: dropping a project, losing the lines that
 * describe one, scoring a project the job description never asked about, or
 * producing a number for a resume that lists no projects at all.
 */
class ProjectRelevanceServiceTest {

    private final ResumeProfileService profileService =
            new ResumeProfileService(new ExperienceRequirementParser(Clock.systemUTC()));
    private final ProjectRelevanceService service = new ProjectRelevanceService(configuration());

    private static ScoringConfiguration configuration() {
        ScoringConfiguration configuration = new ScoringConfiguration();
        configuration.validate();
        return configuration;
    }

    private ResumeProfile profile(String text) {
        return profileService.build(text);
    }

    /**
     * Requirements come from the real extractor rather than being hand-built. A
     * hand-built requirement would have no normalization, and the matching this
     * service relies on is driven entirely by that field - a stub would test the
     * stub.
     */
    private List<Requirement> requirements(String... skillLines) {
        String jd = "Required Skills:\n\n"
                + String.join("\n", java.util.Arrays.stream(skillLines)
                        .map(line -> "* " + line).toList())
                + "\n\nResponsibilities:\n\n* Build and maintain backend services.\n";
        return new JobDescriptionExtractionService(
                new RequirementNormalizationService(),
                new ExperienceRequirementParser(Clock.systemUTC()))
                .extract(jd).requirements();
    }

    @Test
    @DisplayName("A resume with no projects section is not scored, and says so")
    void noProjectsIsUnscoredRatherThanZero() {
        ResumeProfile profile = profile("""
                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2023–Present
                * Built and maintained backend services using Java.
                """);

        List<ProjectAssessment> assessments = service.assess(profile, requirements("Strong programming skills in Java"));

        assertTrue(assessments.isEmpty(),
                "there is no project evidence here, and inventing a 0% would read as a measurement");
    }

    @Test
    @DisplayName("The first project of a projects section is kept, with all its lines")
    void theFirstProjectIsNotLost() {
        ResumeProfile profile = profile("""
                Technical Skills:
                * Java

                Experience:
                Software Engineer — Acme | 2023–Present
                * Built and maintained backend services using Java.

                Projects:
                Ledger Service (2023)
                * Built a Spring Boot REST API for a double-entry ledger.
                * Designed PostgreSQL schemas handling 12M rows per day.
                * Deployed to AWS EC2.
                Pipeline Dashboard (2022)
                * Built a Spring Boot REST API for throughput metrics.
                """);

        List<ProjectAssessment> assessments = service.assess(profile, requirements("Strong programming skills in Java"));

        assertEquals(2, assessments.size(), "one block per project, not one per line");
        ProjectAssessment first = assessments.get(0);
        assertEquals("Ledger Service (2023)", first.title());
        assertEquals(3, first.descriptions().size(),
                "a project loses evidence when only some of its lines are attached to it");
        assertEquals("Pipeline Dashboard (2022)", assessments.get(1).title());
    }

    @Test
    @DisplayName("A project on the job description's stack outranks one that is not")
    void relevanceFollowsTheJobDescription() {
        ResumeProfile onTarget = profile("""
                Experience:
                Software Engineer — Acme | 2023–Present
                * Built backend services.

                Projects:
                Payments API (2023)
                * Built a REST API with PostgreSQL and Docker.
                """);
        ResumeProfile offTarget = profile("""
                Experience:
                Software Engineer — Acme | 2023–Present
                * Built backend services.

                Projects:
                Bake-Off Timer (2022)
                * Tracked rising times for sourdough loaves in a spreadsheet.
                """);

        List<Requirement> jd = requirements("Experience developing REST APIs",
                "Strong knowledge of PostgreSQL / SQL");
        double matched = service.assess(onTarget, jd).stream()
                .mapToDouble(ProjectAssessment::relevanceScore).average().orElseThrow();
        double unmatched = service.assess(offTarget, jd).stream()
                .mapToDouble(ProjectAssessment::relevanceScore).average().orElseThrow();

        assertTrue(matched > unmatched,
                "a project on the job description's stack must score above one that is not: "
                        + matched + " vs " + unmatched);
    }

    @Test
    @DisplayName("A shipped, measured result is distinguished from merely having built something")
    void aQuantifiedOutcomeIsRecognised() {
        ResumeProfile delivered = profile("""
                Experience:
                Software Engineer — Acme | 2023–Present
                * Built backend services.

                Projects:
                Ledger Service (2023)
                * Built a REST API for a double-entry ledger.
                * Cut month-end close from 4 days to 6 hours.
                """);

        ProjectAssessment project = service.assess(delivered, requirements("Experience developing REST APIs"))
                .get(0);

        assertTrue(project.hasQuantifiedOutcome(),
                "'cut month-end close from 4 days to 6 hours' is a result, and saying so is the "
                        + "difference between having built something and having improved something");
    }

    @Test
    @DisplayName("Project scores stay inside a sane range, whatever the input")
    void scoresAreBounded() {
        ResumeProfile profile = profile("""
                Experience:
                Software Engineer — Acme | 2023–Present
                * Built backend services.

                Projects:
                Everything (2023)
                * Built a REST API with PostgreSQL, Docker, AWS, Java, Spring Boot and Redis.
                * Cut API response time by 40% across three services.
                """);

        for (ProjectAssessment assessment : service.assess(profile, requirements(
                "Experience developing REST APIs",
                "Strong knowledge of PostgreSQL / SQL",
                "Strong experience with Docker"))) {
            assertTrue(assessment.relevanceScore() >= 0 && assessment.relevanceScore() <= 100,
                    "a relevance score outside 0-100 is not a percentage: " + assessment.relevanceScore());
        }
    }

    @Test
    @DisplayName("Technologies are named by what the resume says, not by what the engine guessed")
    void technologyNamesComeFromTheResume() {
        ResumeProfile profile = profile("""
                Experience:
                Software Engineer — Acme | 2023–Present
                * Built backend services.

                Projects:
                Ledger Service (2023)
                * Built a REST API with PostgreSQL and Docker.
                """);

        ProjectAssessment project = service.assess(profile, requirements("Experience developing REST APIs"))
                .get(0);

        assertTrue(project.technologies().contains("REST_API") || project.technologies().contains("REST APIs"),
                "the resume says REST API, so that is what should be reported: " + project.technologies());
        assertFalse(project.technologies().contains("REDIS"),
                "nothing in this resume mentions Redis, and inventing it would be a fabrication");
    }

}