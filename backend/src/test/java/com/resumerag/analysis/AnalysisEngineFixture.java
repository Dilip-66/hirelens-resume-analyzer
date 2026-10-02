package com.resumerag.analysis;

import com.resumerag.analysis.assess.AtsQualityService;
import com.resumerag.analysis.assess.EducationAssessmentService;
import com.resumerag.analysis.assess.ProjectRelevanceService;
import com.resumerag.analysis.evidence.RequirementMatchingService;
import com.resumerag.analysis.evidence.ResumeEvidenceRetrievalService;
import com.resumerag.analysis.evidence.ResumeProfileService;
import com.resumerag.analysis.evidence.SemanticEvidenceMatcher;
import com.resumerag.analysis.extraction.ExperienceRequirementParser;
import com.resumerag.analysis.extraction.JobDescriptionExtractionService;
import com.resumerag.analysis.extraction.RequirementNormalizationService;
import com.resumerag.analysis.scoring.AnalysisRecommendationService;
import com.resumerag.analysis.scoring.AnalysisScoringService;
import com.resumerag.analysis.scoring.ScoringConfiguration;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Builds a fully wired {@link AnalysisEngine} with no Spring context, no database
 * and no model.
 *
 * <p>Two reasons this exists rather than a {@code @SpringBootTest}. First, the
 * whole point of the rewrite is that the score is a pure function of the inputs,
 * and a test that needs a database to prove it is not proving that. Second, the
 * semantic layer is stubbed by default, so a test run does not silently depend on
 * a local Ollama being up - a suite that passes or fails based on whether a model
 * happens to be running is not a regression test.
 *
 * <p>Where a test genuinely needs semantics, it passes its own matcher through
 * {@link #withSemanticMatcher}.
 */
public final class AnalysisEngineFixture {

    private AnalysisEngineFixture() {}

    /** The validation case: a full-stack JD against a strong resume. */
    public static final String FULL_STACK_JD = """
            Full-Stack Software Developer

            Experience: 1\u20133 years

            Required Skills:

            * Strong programming skills in Java
            * Experience with Spring Boot
            * Frontend development using React
            * Knowledge of JavaScript and/or TypeScript
            * Experience developing REST APIs
            * Strong knowledge of PostgreSQL / SQL
            * Familiarity with Git and GitHub
            * Experience with Docker
            * Basic experience with AWS or cloud platforms
            * Good problem-solving and debugging skills
            * Understanding of software development lifecycle
            * 1\u20133 years of software development experience

            Preferred Skills:

            * Microservices architecture
            * Python
            * Redis or caching
            * CI/CD pipelines
            * GitHub Actions
            * AWS EC2 / S3
            * Automated testing
            * Basic knowledge of AI/ML

            Responsibilities:

            * Develop and maintain backend services using Java and Spring Boot.
            * Build responsive web interfaces using React, JavaScript, and TypeScript.
            * Design and integrate RESTful APIs.
            * Work with PostgreSQL databases and optimize queries.
            * Containerize applications using Docker.
            * Deploy and maintain applications on cloud platforms.
            * Collaborate using Git and GitHub.
            * Debug issues and improve application performance.
            * Participate in code reviews.
            * Write clean, maintainable, and scalable code.
            """;

    public static final String ARJUN_RESUME = """
            Arjun Rao

            Experience:
            2.5 years

            Summary:
            Software Developer with 2.5 years of experience building scalable full-stack web applications
            using Java, Spring Boot, React, PostgreSQL and AWS.

            Technical Skills:

            * Java
            * Spring Boot
            * React
            * TypeScript
            * JavaScript
            * REST APIs
            * PostgreSQL
            * Docker
            * AWS
            * Git/GitHub
            * Microservices
            * Redis
            * CI/CD
            * Python

            Experience:
            Software Developer \u2014 TechNova Solutions | 2024\u2013Present

            * Developed REST APIs using Java and Spring Boot for enterprise web applications.
            * Built responsive React and TypeScript dashboards and reusable UI components.
            * Designed PostgreSQL schemas and optimized SQL queries for production workloads.
            * Dockerized microservices and deployed applications on AWS EC2 and S3.
            * Implemented GitHub Actions CI/CD pipelines and automated testing workflows.
            * Reduced API response time by 30% through caching and query optimization.

            Education:
            Bachelor's Degree \u2014 Relevant field of study
            """;

    /** A frontend-only candidate: no Java, no Spring Boot. */
    public static final String FRONTEND_ONLY_RESUME = """
            Priya Shah

            Experience:
            2 years

            Technical Skills:

            * React
            * JavaScript
            * TypeScript
            * HTML
            * CSS
            * Git

            Experience:
            Frontend Developer \u2014 Brightwave Digital | 2023\u2013Present

            * Built responsive React and TypeScript dashboards and reusable UI components.
            * Developed REST APIs consumed by three client applications.
            * Partnered with backend engineers on API contracts and integration.
            """;

    /**
     * The date the benchmark treats as "today".
     *
     * <p>Pinned, because an open-ended "2021 - Present" role would otherwise make
     * every score depend on the day the suite ran - the numbers would drift
     * monthly and no failure would ever be attributable to a change.
     */
    public static final Clock BENCHMARK_CLOCK = Clock.fixed(
            Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC);

    public static ScoringConfiguration scoringConfiguration() {
        ScoringConfiguration configuration = new ScoringConfiguration();
        configuration.validate();
        return configuration;
    }

    public static AnalysisEngine engine() {
        return engine(new SemanticEvidenceMatcher() {
            @Override
            public Index indexFor(UUID resumeId) {
                return new Index() {
                    @Override
                    public List<List<Hit>> rankAll(List<String> requirementTexts, int limit) {
                        return requirementTexts.stream().map(text -> List.<Hit>of()).toList();
                    }

                    @Override
                    public List<Hit> rank(String requirementText, int limit) {
                        return List.of();
                    }
                };
            }
        });
    }

    public static AnalysisEngine engine(SemanticEvidenceMatcher semanticEvidenceMatcher) {
        return engine(semanticEvidenceMatcher, scoringConfiguration());
    }

    public static AnalysisEngine engine(SemanticEvidenceMatcher semanticEvidenceMatcher,
                                        ScoringConfiguration configuration) {
        return engine(semanticEvidenceMatcher, configuration, BENCHMARK_CLOCK);
    }

    public static AnalysisEngine engine(SemanticEvidenceMatcher semanticEvidenceMatcher,
                                        ScoringConfiguration configuration, Clock clock) {
        ExperienceRequirementParser experienceParser = new ExperienceRequirementParser(clock);
        return new AnalysisEngine(
                new JobDescriptionExtractionService(new RequirementNormalizationService(), experienceParser),
                new ResumeProfileService(experienceParser),
                new ResumeEvidenceRetrievalService(semanticEvidenceMatcher, configuration),
                new RequirementMatchingService(configuration),
                new AnalysisScoringService(configuration),
                new AnalysisRecommendationService(),
                new ProjectRelevanceService(configuration),
                new EducationAssessmentService(),
                new AtsQualityService(configuration));
    }
}
