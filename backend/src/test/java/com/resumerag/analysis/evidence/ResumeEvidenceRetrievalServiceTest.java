package com.resumerag.analysis.evidence;

import com.resumerag.analysis.extraction.SynonymRegistry;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.DemandLevel;
import com.resumerag.analysis.model.NormalizedTerm;
import com.resumerag.analysis.model.NormalizedRequirement;
import com.resumerag.analysis.model.CompoundOperator;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Evidence retrieval, with the semantic layer stubbed out.
 *
 * <p>The property under test is that evidence <em>strength</em> depends on where
 * a technology appears and how much the job description demanded - not on the fact
 * that the word appeared. That is the distinction a keyword counter cannot make,
 * and it is what the 0-4 scale exists to express.
 */
class ResumeEvidenceRetrievalServiceTest {

    private final ScoringConfiguration configuration = new ScoringConfiguration();
    private final ResumeEvidenceRetrievalService service = new ResumeEvidenceRetrievalService(
            new SemanticEvidenceMatcher() {
                @Override
                public Index indexFor(java.util.UUID resumeId) {
                    return new Index() {
                        @Override
                        public List<List<Hit>> rankAll(List<String> texts, int limit) {
                            return texts.stream().map(t -> List.<Hit>of()).toList();
                        }

                        @Override
                        public List<Hit> rank(String text, int limit) {
                            return List.of();
                        }
                    };
                }
            }, configuration);

    private final ResumeProfileService profileService =
            new ResumeProfileService(new com.resumerag.analysis.extraction.ExperienceRequirementParser(java.time.Clock.systemUTC()));

    @Test
    @DisplayName("The same word is different evidence in a role and in a skills list")
    void theSameWordIsDifferentEvidenceInDifferentPlaces() {
        ResumeProfile profile = profileService.build("""
                Technical Skills:
                * Docker

                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * Dockerized microservices and deployed them to production.
                """);

        int inRole = strongestLevel(profile, requirement("Experience with Docker", DemandLevel.PROFESSIONAL));
        int inList = strongestLevel(profileService.build("""
                Technical Skills:
                * Docker
                """), requirement("Experience with Docker", DemandLevel.PROFESSIONAL));

        assertEquals(4, inRole, "using a technology in a role is the strongest evidence a resume can offer");
        assertEquals(3, inList, "listing it is a claim about a person, not a description of work");
        assertNotEquals(inRole, inList);
    }

    @Test
    @DisplayName("A listed skill is only partial against an advanced requirement")
    void aListedSkillIsOnlyPartialAgainstAnAdvancedRequirement() {
        ResumeProfile listed = profileService.build("Technical Skills:\n* AWS");

        int professional = strongestLevel(listed, requirement("Advanced AWS deployment", DemandLevel.ADVANCED));
        int basic = strongestLevel(listed, requirement("Basic experience with AWS", DemandLevel.BASIC));

        assertEquals(professional, basic,
                "the evidence is the same; what differs is what the job asked for, and that is read from "
                        + "the requirement rather than from the resume");
        // The difference is applied at classification, and is covered end to end in
        // AnalysisEngineRegressionTest (Test 6). Here we only assert the evidence
        // itself does not change.
    }

    @Test
    @DisplayName("A quantified result is one step above a bare contextual overlap")
    void aQuantifiedResultIsStrongerContextualEvidence() {
        ResumeProfile quantified = profileService.build("""
                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * Reduced API response time by 30% through caching and query optimization.
                """);
        ResumeProfile bare = profileService.build("""
                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * Worked on caching and query optimization.
                """);

        // The requirement is about performance; neither line uses the word, so both
        // are contextual evidence and the quantified one has to be the better of
        // the two.
        Requirement performance = requirement("Improve application performance", DemandLevel.PROFESSIONAL);
        assertTrue(strongestLevel(quantified, performance) > strongestLevel(bare, performance),
                "a demonstrated outcome is stronger than an unquantified mention of the same work");
    }

    @Test
    @DisplayName("A category requirement is satisfied by a concrete member")
    void aCategoryRequirementIsSatisfiedByAMember() {
        ResumeProfile profile = profileService.build("""
                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * Deployed applications on AWS EC2 and S3.
                """);

        int level = strongestLevel(profile,
                requirement("Deploy and maintain applications on cloud platforms", DemandLevel.PROFESSIONAL));
        assertEquals(4, level,
                "naming three AWS services in a role is direct evidence of cloud deployment, even though "
                        + "the words 'cloud platform' never appear");
    }

    @Test
    @DisplayName("Evidence is ranked strongest first, and stably")
    void evidenceIsRankedStrongestFirstAndStably() {
        ResumeProfile profile = profileService.build("""
                Technical Skills:
                * Java

                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * Built services with Java and Spring Boot.
                """);

        List<com.resumerag.analysis.model.EvidenceCandidate> first =
                service.evidenceFor(profile, requirement("Experience with Java", DemandLevel.PROFESSIONAL), List.of());
        List<com.resumerag.analysis.model.EvidenceCandidate> second =
                service.evidenceFor(profile, requirement("Experience with Java", DemandLevel.PROFESSIONAL), List.of());

        assertEquals(first.stream().map(com.resumerag.analysis.model.EvidenceCandidate::text).toList(),
                second.stream().map(com.resumerag.analysis.model.EvidenceCandidate::text).toList(),
                "the evidence shown as the reason must not change between identical runs");
        assertEquals(com.resumerag.analysis.model.EvidenceStrength.DIRECT_PROFESSIONAL,
                first.get(0).strength());
    }

    @Test
    @DisplayName("An unrelated technology is not treated as a match")
    void unrelatedTechnologiesAreNotMatches() {
        ResumeProfile profile = profileService.build("Technical Skills:\n* Java\n* PostgreSQL");

        // A requirement the resume never mentions must produce no evidence at all,
        // which is what makes NOT_EXPLICITLY_MENTIONED reachable rather than a
        // silent fallback.
        assertTrue(service.evidenceFor(profile,
                requirement("Experience with Kubernetes", DemandLevel.PROFESSIONAL), List.of()).isEmpty());
        assertTrue(service.evidenceFor(profile,
                requirement("Experience with Ruby on Rails", DemandLevel.PROFESSIONAL), List.of()).isEmpty());
    }

    @Test
    @DisplayName("Short terms are matched on word boundaries only")
    void shortTermsRespectWordBoundaries() {
        ResumeProfile profile = profileService.build("""
                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * He said the project was going well and stayed on schedule.
                """);

        // "Go" must not fire inside "going", and "AI" must not fire inside "said".
        // Substring matching on a short closed vocabulary produces confidently
        // wrong matches.
        assertTrue(service.evidenceFor(profile, requirement("Go", DemandLevel.BASIC), List.of()).isEmpty(),
                "'going' is not evidence of Go");
        assertTrue(service.evidenceFor(profile, requirement("AI/ML", DemandLevel.BASIC), List.of()).isEmpty(),
                "'said' is not evidence of AI");
    }

    @Test
    @DisplayName("Pluralised technology names still match")
    void pluralisedNamesStillMatch() {
        ResumeProfile profile = profileService.build("""
                Experience:
                Engineer \u2014 Acme | 2023\u2013Present
                * Developed REST APIs using Java and Spring Boot.
                """);

        // The single most common form of this false negative: a job description
        // says "REST API" and every candidate writes "REST APIs".
        assertFalse(service.evidenceFor(profile,
                requirement("Experience with REST API", DemandLevel.PROFESSIONAL), List.of()).isEmpty());
    }

    @Test
    @DisplayName("The registry does not equate unrelated technologies")
    void theRegistryDoesNotEquateUnrelatedTechnologies() {
        assertEquals("REST_API", SynonymRegistry.lookup("restful services").orElseThrow().canonicalKey());
        assertEquals("POSTGRESQL", SynonymRegistry.lookup("Postgres").orElseThrow().canonicalKey());
        assertEquals("AWS", SynonymRegistry.lookup("Amazon Web Services").orElseThrow().canonicalKey());

        // These are the pairs a too-eager synonym table would collapse, and
        // collapsing them would tell a candidate they have a skill they do not.
        assertTrue(SynonymRegistry.lookup("GraphQL").isEmpty()
                        || !SynonymRegistry.lookup("GraphQL").orElseThrow().canonicalKey().equals("REST_API"),
                "GraphQL and REST are different requirements");
        assertNotEquals(SynonymRegistry.lookup("Azure").orElseThrow().canonicalKey(),
                SynonymRegistry.lookup("AWS").orElseThrow().canonicalKey(),
                "Azure and AWS are different cloud providers, and one is not evidence of the other");
    }

    private int strongestLevel(ResumeProfile profile, Requirement requirement) {
        return service.evidenceFor(profile, requirement, List.of()).stream()
                .mapToInt(c -> c.strength().level())
                .max()
                .orElse(0);
    }

    private Requirement requirement(String sourceText, DemandLevel demand) {
        NormalizedRequirement normalized =
                new com.resumerag.analysis.extraction.RequirementNormalizationService().normalize(sourceText);
        return new Requirement(0, normalized.displayName(), RequirementCategory.REQUIRED_SKILL,
                RequirementImportance.HIGH, demand, normalized.canonicalKey(), List.of(), sourceText,
                true, normalized);
    }
}
