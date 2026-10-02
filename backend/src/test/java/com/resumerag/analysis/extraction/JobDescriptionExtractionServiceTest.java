package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.CompoundOperator;
import com.resumerag.analysis.model.NormalizedRequirement;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.DemandLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The requirement extraction layer, tested on the awkward cases.
 *
 * <p>Each of these is a way a flat keyword extractor gets a specific thing wrong
 * that a user would notice: counting one capability twice, inventing a second
 * mandatory language out of "and/or", reading a ceiling as a requirement, or
 * turning a job title into a gap.
 */
class JobDescriptionExtractionServiceTest {

    private final JobDescriptionExtractionService service = new JobDescriptionExtractionService(
            new RequirementNormalizationService(), new ExperienceRequirementParser(java.time.Clock.systemUTC()));

    @Test
    @DisplayName("A required skill and a preferred skill never land in the same bucket")
    void requiredAndPreferredAreSeparated() {
        String jd = """
                Required Skills:
                * Strong programming skills in Java
                * Experience with Docker

                Preferred Skills:
                * Python
                * Redis
                """;

        JobDescriptionProfile profile = service.extract(jd);

        assertEquals(2, profile.countIn(RequirementCategory.REQUIRED_SKILL));
        assertEquals(2, profile.countIn(RequirementCategory.PREFERRED_SKILL));

        // And the preferred ones are the ones the JD called preferred. This is the
        // misclassification that made a missing bonus cost the same as a missing
        // core skill.
        List<Requirement> preferred = profile.inCategory(RequirementCategory.PREFERRED_SKILL);
        assertTrue(preferred.stream().anyMatch(r -> r.name().equals("Python")));
        assertTrue(preferred.stream().anyMatch(r -> r.name().equals("Redis")));
        assertFalse(preferred.stream().anyMatch(r -> r.name().equals("Java")));
    }

    @Test
    @DisplayName("\"and/or\" stays one requirement, satisfied by either language")
    void andOrIsNotTwoMandatoryRequirements() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Knowledge of JavaScript and/or TypeScript
                """);

        assertEquals(1, profile.countIn(RequirementCategory.REQUIRED_SKILL),
                "'and/or' must not become two separate mandatory requirements");
        Requirement requirement = profile.requirements().get(0);
        assertEquals(2, requirement.normalization().terms().size());
        assertFalse(requirement.normalization().requiresAllTerms(),
                "and/or is read as a disjunction: the employer said one was enough");
        assertEquals(CompoundOperator.AND_OR, requirement.normalization().operator());
    }

    @Test
    @DisplayName("A slash between two technologies is a disjunction")
    void slashIsADisjunction() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Strong knowledge of PostgreSQL / SQL
                * AWS EC2 / S3
                """);

        assertEquals(2, profile.countIn(RequirementCategory.REQUIRED_SKILL));
        for (Requirement requirement : profile.inCategory(RequirementCategory.REQUIRED_SKILL)) {
            assertFalse(requirement.normalization().requiresAllTerms(),
                    requirement.name() + " is a disjunction, not a requirement for both");
        }
    }

    @Test
    @DisplayName("A plain \"and\" is a conjunction, and a half-met one is not a match")
    void plainAndIsAConjunction() {
        RequirementNormalizationService normalizer = new RequirementNormalizationService();
        NormalizedRequirement git = normalizer.normalize("Familiarity with Git and GitHub");

        assertEquals(2, git.terms().size());
        assertTrue(git.requiresAllTerms(), "Git AND GitHub needs both; a disjunction here would invent "
                + "a requirement the employer did not state");
    }

    @Test
    @DisplayName("The same capability in different words is one requirement")
    void wordingVariantsAreOneRequirement() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Experience developing REST APIs
                * Experience with RESTful API development
                * Familiarity with REST APIs
                """);

        assertEquals(1, profile.countIn(RequirementCategory.REQUIRED_SKILL),
                "'REST APIs' and 'RESTful API development' are one requirement; counting them three times "
                        + "lets one capability weigh triple");
    }

    @Test
    @DisplayName("A duty is scored separately from a capability")
    void responsibilitiesAreNotSkills() {
        JobDescriptionProfile profile = service.extract("""
                Responsibilities:
                * Design and integrate RESTful APIs
                * Participate in code reviews
                """);

        assertEquals(2, profile.countIn(RequirementCategory.RESPONSIBILITY));
        assertEquals(0, profile.countIn(RequirementCategory.REQUIRED_SKILL),
                "a duty is not a skill requirement, and merging the two would let a candidate who has "
                        + "never done the job score as a match on it");
    }

    @Test
    @DisplayName("A job title is not a requirement")
    void aJobTitleIsNotARequirement() {
        JobDescriptionProfile profile = service.extract("""
                Full-Stack Software Developer

                Required Skills:
                * Experience with Java
                """);

        assertEquals(1, profile.requirements().size(),
                "a title must not become a requirement the candidate can then be shown as missing");
    }

    @Test
    @DisplayName("Prose sections produce no requirements")
    void proseSectionsProduceNoRequirements() {
        JobDescriptionProfile profile = service.extract("""
                About the role

                We are a friendly team who like long walks and short standups.
                """);

        assertTrue(profile.requirements().isEmpty());
    }

    @Test
    @DisplayName("The demand level comes from the JD's own wording")
    void demandLevelComesFromTheJobsWording() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Strong programming skills in Java
                * Advanced Kubernetes
                * Familiarity with Git
                * Basic experience with AWS
                """);

        assertEquals(DemandLevel.PROFESSIONAL, named(profile, "Java").demand(),
                "'Strong programming skills' asks for professional capability");
        assertEquals(DemandLevel.ADVANCED, named(profile, "Kubernetes").demand());
        assertEquals(DemandLevel.BASIC, named(profile, "Git").demand());
        assertEquals(DemandLevel.BASIC, named(profile, "AWS").demand(),
                "'Basic' must win over 'experience', or a listed skill reads as sufficient");
    }

    @Test
    @DisplayName("A strongly worded required skill is more load-bearing than a plain one")
    void strongQualifiersRaiseImportance() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Strong programming skills in Java
                * Familiarity with Git
                """);

        assertEquals(RequirementImportance.CRITICAL, named(profile, "Java").importance());
        assertEquals(RequirementImportance.HIGH, named(profile, "Git").importance());
    }

    @Test
    @DisplayName("The same requirement in two sections is not two requirements")
    void deduplicationIsScopedToTheSection() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Experience with Java

                Preferred Skills:
                * Experience with Java
                """);

        assertEquals(1, profile.countIn(RequirementCategory.REQUIRED_SKILL));
        assertEquals(1, profile.countIn(RequirementCategory.PREFERRED_SKILL),
                "a skill in both lists is genuinely two different requirements with different weight");
    }

    @Test
    @DisplayName("The experience band stated in the header and in the skills list is one requirement")
    void theExperienceBandIsNeverCountedTwice() {
        JobDescriptionProfile profile = service.extract("""
                Experience: 1\u20133 years

                Required Skills:
                * 1\u20133 years of software development experience
                """);

        assertEquals(1, profile.countIn(RequirementCategory.EXPERIENCE),
                "one number stated twice must not become two requirements to be evidenced");
        assertEquals(1.0, profile.experienceRequirement().minYears());
        assertEquals(3.0, profile.experienceRequirement().maxYears());
    }

    @Test
    @DisplayName("Extraction never throws on malformed input")
    void malformedInputIsTolerated() {
        assertTrue(service.extract(null).requirements().isEmpty());
        assertTrue(service.extract("").requirements().isEmpty());
        assertTrue(service.extract("::: /// ***").requirements().isEmpty());
    }

    private Requirement named(JobDescriptionProfile profile, String name) {
        return profile.requirements().stream()
                .filter(r -> r.name().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no requirement named " + name + "; found "
                        + profile.requirements().stream().map(Requirement::name).toList()));
    }

    @Test
    @DisplayName("Every extracted requirement keeps the line it came from")
    void everyRequirementKeepsItsSourceLine() {
        JobDescriptionProfile profile = service.extract("""
                Required Skills:
                * Experience with Spring Boot
                """);

        Requirement requirement = profile.requirements().get(0);
        assertNotNull(requirement.sourceText());
        assertEquals("Experience with Spring Boot", requirement.sourceText(),
                "the report has to be able to show what the JD actually said");
        assertTrue(requirement.explicitRequirement());
        assertNull(requirement.normalizedName().isBlank() ? "" : null);
    }
}
