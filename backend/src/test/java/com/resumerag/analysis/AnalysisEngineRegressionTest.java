package com.resumerag.analysis;


import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoreCategory;
import com.resumerag.analysis.model.ScoredAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The regression suite for the scoring rewrite, driven by the validation case.
 *
 * <p><b>On what these assert.</b> No test pins an exact overall score. The score
 * is a weighted mean over seven configurable weights, and a test that hard-codes
 * the result would break every time anyone tunes one of them - while checking
 * nothing about behaviour. What is asserted instead is the behaviour that matters:
 * which requirements matched, on what evidence, which did not, and how far the
 * score moves when the candidate's evidence changes. A scoring bug changes those;
 * a tuning change does not.
 *
 * <p>Semantic matching is stubbed out, so these run identically with or without a
 * local LLM and cannot flake because a model is slow.
 */
class AnalysisEngineRegressionTest {

    private final AnalysisEngine engine = AnalysisEngineFixture.engine();

    // ---------------------------------------------------------------------
    // Test 1 - strong candidate
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 1: a strong candidate matches the whole documented stack")
    void strongCandidateMatchesTheDocumentedStack() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        // Every technology the resume actually names and uses.
        assertMatched(result, "Java");
        assertMatched(result, "Spring Boot");
        assertMatched(result, "React");
        assertMatched(result, "JavaScript or TypeScript");
        assertMatched(result, "REST APIs");
        assertMatched(result, "PostgreSQL or SQL");
        assertMatched(result, "Git + GitHub");
        assertMatched(result, "Docker");
        assertMatched(result, "AWS or Cloud platforms");
        assertMatched(result, "Microservices");
        assertMatched(result, "Redis or Caching");
        assertMatched(result, "CI/CD");
        assertMatched(result, "GitHub Actions");
        assertMatched(result, "AWS EC2 or AWS S3");
        assertMatched(result, "Automated testing");
        assertMatched(result, "Python");

        assertEquals(com.resumerag.analysis.model.ExperienceAlignment.Status.WITHIN_RANGE,
                result.experienceAlignment().status());
        assertEquals(2.5, result.experienceAlignment().candidateYears());
        assertEquals(1.0, result.experienceAlignment().requiredMinYears());
        assertEquals(3.0, result.experienceAlignment().requiredMaxYears());

        // And the two requirements the resume genuinely never mentions.
        assertStatus(result, "Code reviews", MatchStatus.NOT_EXPLICITLY_MENTIONED);
        assertStatus(result, "Artificial intelligence or Machine learning", MatchStatus.NOT_EXPLICITLY_MENTIONED);
    }

    @Test
    @DisplayName("Test 1: using a technology in a role outranks listing it as a skill")
    void professionalUseOutranksASkillListMention() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        // "Dockerized microservices and deployed applications on AWS EC2 and S3" is
        // the strongest evidence a resume can offer; a skill list is a claim.
        RequirementMatch docker = required(result, "Docker");
        assertEquals(4, docker.evidenceStrength());
        assertTrue(docker.resumeEvidence().stream()
                        .anyMatch(e -> e.contains("Dockerized microservices")),
                "the evidence shown must be the sentence that decided the match, got: "
                        + docker.resumeEvidence());

        // Python appears only in the skills list, so it is a match on a listed
        // skill - lower strength and lower confidence than demonstrated work.
        RequirementMatch python = find(result, "Python");
        assertEquals(RequirementCategory.PREFERRED_SKILL, python.category());
        assertEquals(3, python.evidenceStrength());
        assertTrue(python.confidence() < docker.confidence(),
                "a listed skill must carry less confidence than demonstrated work");
    }

    @Test
    @DisplayName("Test 1: soft requirements are evidenced weakly, not overclaimed")
    void softRequirementsAreNotOverclaimed() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        // The resume demonstrates performance work and nothing about debugging, so
        // the combined requirement is partial - not a full match.
        RequirementMatch debugging = find(result, "Debugging + Application performance");
        assertEquals(MatchStatus.PARTIAL_MATCH, debugging.status());

        // The resume never says "software development lifecycle"; CI/CD and testing
        // workflows are supporting evidence at best.
        RequirementMatch sdlc = required(result, "Software development lifecycle");
        assertNotEquals(MatchStatus.EXPLICIT_MATCH, sdlc.status(),
                "a requirement the resume never names must not be reported as explicitly met");
        assertFalse(sdlc.resumeEvidence().isEmpty(),
                "a weaker match must still say what it based that on");
    }

    @Test
    @DisplayName("Test 1: an unmet requirement is reported as silence, never as a deficiency")
    void unmetRequirementsAreReportedAsSilence() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        RequirementMatch codeReview = find(result, "Code reviews");
        assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, codeReview.status());
        assertTrue(codeReview.explanation().toLowerCase().contains("not explicitly mentioned"),
                "the wording must state what was observed, got: " + codeReview.explanation());
        assertFalse(codeReview.explanation().toLowerCase().contains("cannot"),
                "a resume that does not mention a skill is not evidence the candidate lacks it");
        assertFalse(codeReview.explanation().toLowerCase().contains("lack"),
                "a resume that does not mention a skill is not evidence the candidate lacks it");
    }

    @Test
    @DisplayName("Test 1: dimensions that were never measured are null, not invented")
    void unmeasuredDimensionsAreNull() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        // This resume describes no projects and this job description asks for no
        // degree, so both stay unmeasured. Reporting a number either way would be a
        // fabrication, and a reader cannot tell an invented 80% from a measured one.
        assertNull(result.category(ScoreCategory.PROJECTS).score());
        assertNull(result.category(ScoreCategory.EDUCATION).score());
        assertNotNull(result.category(ScoreCategory.PROJECTS).note(),
                "an unmeasured dimension must say why it is missing");
        assertNotNull(result.category(ScoreCategory.EDUCATION).note());

        // ATS quality is measurable from any resume text, so it is assessed - and
        // its note has to say it is measuring text, not layout.
        assertNotNull(result.category(ScoreCategory.ATS).score());
        assertTrue(result.category(ScoreCategory.ATS).note().toLowerCase().contains("text"),
                "the ATS note must be explicit that only text was assessed");

        assertNotNull(result.category(ScoreCategory.REQUIRED_SKILLS).score());
        assertNotNull(result.category(ScoreCategory.EXPERIENCE).score());
        assertNotNull(result.category(ScoreCategory.RESPONSIBILITIES).score());
        assertNotNull(result.category(ScoreCategory.PREFERRED_SKILLS).score());
    }

    @Test
    @DisplayName("Test 1: the score is reproducible for identical input")
    void theSameInputsProduceTheSameScore() {
        int first = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD).overallScore();
        int second = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD).overallScore();
        assertEquals(first, second, "a score that moves between identical runs cannot be defended");
    }

    // ---------------------------------------------------------------------
    // Test 2 - missing critical skill
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 2: losing the core backend stack drops the score substantially")
    void losingTheCoreStackDropsTheScore() {
        ScoredAnalysis strong = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);
        ScoredAnalysis frontendOnly =
                analyze(AnalysisEngineFixture.FRONTEND_ONLY_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        assertStatus(frontendOnly, "Java", MatchStatus.NOT_EXPLICITLY_MENTIONED);
        assertStatus(frontendOnly, "Spring Boot", MatchStatus.NOT_EXPLICITLY_MENTIONED);
        assertMatched(frontendOnly, "React");

        double drop = strong.overallScore() - frontendOnly.overallScore();
        assertTrue(drop >= 10,
                "a candidate with no Java and no Spring Boot should lose well over ten points; "
                        + "strong=" + strong.overallScore() + " frontendOnly=" + frontendOnly.overallScore());

        // The dimension that actually moved is the required-skills one, which is
        // where a missing core stack belongs.
        assertTrue(frontendOnly.category(ScoreCategory.REQUIRED_SKILLS).score()
                        < strong.category(ScoreCategory.REQUIRED_SKILLS).score() - 15,
                "required skills must fall sharply when the core stack is absent");
    }

    // ---------------------------------------------------------------------
    // Test 3 - missing preferred skill
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 3: a missing preferred skill barely moves the score")
    void aMissingPreferredSkillBarelyMatters() {
        String withoutAiMl = AnalysisEngineFixture.ARJUN_RESUME;
        String withAiMl = AnalysisEngineFixture.ARJUN_RESUME
                .replace("* Python", "* Python\n* AI/ML\n* Machine Learning");

        ScoredAnalysis without = analyze(withoutAiMl, AnalysisEngineFixture.FULL_STACK_JD);
        ScoredAnalysis with = analyze(withAiMl, AnalysisEngineFixture.FULL_STACK_JD);

        assertStatus(without, "Artificial intelligence or Machine learning", MatchStatus.NOT_EXPLICITLY_MENTIONED);
        assertMatched(with, "Artificial intelligence or Machine learning");

        double difference = Math.abs(with.overallScore() - without.overallScore());
        assertTrue(difference <= 3,
                "a bonus skill is worth at most a couple of points; difference was " + difference);
        assertTrue(without.overallScore() >= 70,
                "missing a preferred skill must not push a strong candidate out of the strong band; got "
                        + without.overallScore());

        // And the asymmetry itself: the same skill, required rather than preferred.
        String jdRequiringAi = AnalysisEngineFixture.FULL_STACK_JD
                .replace("* Basic knowledge of AI/ML", "* Strong experience with AI/ML in production systems")
                .replace("* Understanding of software development lifecycle", "");
        ScoredAnalysis required = analyze(withoutAiMl, jdRequiringAi);
        assertStatus(required, "Artificial intelligence or Machine learning", MatchStatus.NOT_EXPLICITLY_MENTIONED);
    }

    @Test
    @DisplayName("Test 3: preferred skills cannot rescue missing required ones")
    void preferredSkillsCannotRescueMissingRequiredOnes() {
        String jd = """
                Backend Engineer

                Required Skills:
                * Strong experience with Kubernetes
                * Strong experience with Terraform

                Preferred Skills:
                * Python
                * Redis
                * Kubernetes (nice to have)
                * Terraform (nice to have)
                * GraphQL
                """;
        // A candidate with every bonus skill and neither required one.
        String resume = """
                Sam Reed

                Technical Skills:
                * Python
                * Redis
                * GraphQL

                Experience:
                Backend Developer \u2014 Kite Systems | 2023\u2013Present
                * Built Python services with Redis caching and a GraphQL API.
                """;

        ScoredAnalysis result = analyze(resume, jd);
        assertTrue(result.overallScore() < 60,
                "a full set of bonus skills must not produce a passing score when the required stack is "
                        + "absent; got " + result.overallScore());
    }

    // ---------------------------------------------------------------------
    // Test 4 - experience mismatch
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 4: a candidate under the stated minimum scores poorly on experience")
    void experienceShortfallIsScoredProportionally() {
        String jd = """
                Senior Backend Engineer

                Experience: 3\u20135 years

                Required Skills:
                * Experience with Java
                """;
        String resume = """
                Jordan Lee

                Experience:
                1 year

                Technical Skills:
                * Java

                Experience:
                Backend Developer \u2014 Northwind | 2024\u2013Present
                * Developed REST APIs using Java and Spring Boot.
                """;

        ScoredAnalysis underBand = analyze(resume, jd);
        var experience = underBand.experienceAlignment();

        assertEquals(com.resumerag.analysis.model.ExperienceAlignment.Status.BELOW_RANGE, experience.status());
        assertEquals(3.0, experience.requiredMinYears());
        assertEquals(5.0, experience.requiredMaxYears());
        assertEquals(1.0, experience.candidateYears());
        assertNotNull(experience.score());
        assertTrue(experience.score() < 50,
                "one year against a three-year minimum is a third of the experience, not nearly enough; got "
                        + experience.score());

        // The shortfall has to reach the headline number. Measured against the same
        // candidate against a band that fits them, so the only thing that changed
        // is the experience requirement.
        String juniorJd = jd.replace("3\u20135 years", "1\u20132 years").replace("Senior", "Junior");
        ScoredAnalysis inBand = analyze(resume, juniorJd);
        assertTrue(underBand.overallScore() < inBand.overallScore(),
                "the same resume must score lower against a band it falls short of: underBand="
                        + underBand.overallScore() + " inBand=" + inBand.overallScore());
        assertNotEquals(inBand.matchLabel(), underBand.matchLabel(),
                "a band shortfall large enough to change the score should change the label too");
    }

    @Test
    @DisplayName("Test 4: exceeding the stated maximum is not a shortfall")
    void exceedingTheRangeIsNotAPenalty() {
        String jd = """
                Junior Developer

                Experience: 1\u20133 years

                Required Skills:
                * Experience with Java
                """;
        String resume = """
                Alex Turner

                Experience:
                9 years

                Technical Skills:
                * Java

                Experience:
                Developer \u2014 Longtime Systems | 2016\u2013Present
                * Developed REST APIs using Java and Spring Boot.
                """;

        ScoredAnalysis result = analyze(resume, jd);
        assertEquals(com.resumerag.analysis.model.ExperienceAlignment.Status.ABOVE_RANGE,
                result.experienceAlignment().status());
        assertEquals(100.0, result.experienceAlignment().score(),
                "a nine-year candidate is stronger than a 1-3 year band, not mismatched by it");
    }

    // ---------------------------------------------------------------------
    // Test 5 - exact semantic match
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 5: RESTful APIs in the JD matches REST APIs on the resume")
    void restfulApisMatchesRestApis() {
        String jd = """
                Backend Engineer

                Required Skills:
                * Experience designing and integrating RESTful APIs
                """;
        String resume = """
                Dana Brooks

                Experience:
                Software Engineer \u2014 Helix | 2022\u2013Present
                * Developed REST APIs using Java and Spring Boot.
                """;

        ScoredAnalysis result = analyze(resume, jd);
        RequirementMatch match = result.matches().stream()
                .filter(m -> m.requirement().contains("REST"))
                .findFirst()
                .orElseThrow();

        assertEquals(MatchStatus.EXPLICIT_MATCH, match.status(),
                "'RESTful APIs' and 'REST APIs' are the same requirement in different words");
        assertEquals(4, match.evidenceStrength());
    }

    // ---------------------------------------------------------------------
    // Test 6 - partial evidence
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 6: a listed skill does not satisfy an advanced requirement")
    void aListedSkillDoesNotSatisfyAnAdvancedRequirement() {
        String jd = """
                Cloud Engineer

                Required Skills:
                * Advanced AWS deployment
                """;
        String resume = """
                Kim Patel

                Technical Skills:
                * AWS

                Experience:
                Developer \u2014 Skyline | 2023\u2013Present
                * Built internal tooling for the platform team.
                """;

        ScoredAnalysis result = analyze(resume, jd);
        RequirementMatch aws = result.matches().stream()
                .filter(m -> m.requirement().toLowerCase().contains("aws"))
                .findFirst()
                .orElseThrow();

        assertEquals(MatchStatus.PARTIAL_MATCH, aws.status(),
                "'AWS' in a skills list is not advanced deployment experience");
        assertTrue(aws.evidenceStrength() < 4);
        assertTrue(aws.confidence() < 0.9);
    }

    @Test
    @DisplayName("Test 6: actual deployment work does satisfy an advanced requirement")
    void demonstratedWorkSatisfiesAnAdvancedRequirement() {
        String jd = """
                Cloud Engineer

                Required Skills:
                * Advanced AWS deployment
                """;
        String resume = """
                Kim Patel

                Experience:
                Platform Engineer \u2014 Skyline | 2021\u2013Present
                * Migrated twelve services to AWS EC2 and S3 and automated the rollback path.
                """;

        ScoredAnalysis result = analyze(resume, jd);
        RequirementMatch aws = result.matches().stream()
                .filter(m -> m.requirement().toLowerCase().contains("aws"))
                .findFirst()
                .orElseThrow();

        assertEquals(MatchStatus.EXPLICIT_MATCH, aws.status());
    }

    // ---------------------------------------------------------------------
    // Test 7 - code review
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Test 7: an unmet duty is NOT_EXPLICITLY_MENTIONED, not a lack of ability")
    void unmetDutyIsReportedAsSilence() {
        String jd = """
                Senior Engineer

                Responsibilities:
                * Participate in code reviews
                * Mentor junior developers
                """;
        String resume = """
                Robin Ellis

                Experience:
                Software Engineer \u2014 Ashwood | 2022\u2013Present
                * Shipped billing service features and fixed production incidents.
                """;

        ScoredAnalysis result = analyze(resume, jd);
        RequirementMatch codeReview = find(result, "Code reviews");

        assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, codeReview.status());
        assertEquals(0, codeReview.evidenceStrength());
        assertEquals(0.0, codeReview.confidence());
        assertTrue(codeReview.resumeEvidence().isEmpty(),
                "no evidence must be reported as no evidence, not as a weak example");
    }

    // ---------------------------------------------------------------------
    // Cross-cutting behaviour
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Required and preferred requirements are never merged into one list")
    void requiredAndPreferredStaySeparate() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        long required = result.matchesIn(RequirementCategory.REQUIRED_SKILL).size();
        long preferred = result.matchesIn(RequirementCategory.PREFERRED_SKILL).size();
        long responsibilities = result.matchesIn(RequirementCategory.RESPONSIBILITY).size();
        long experience = result.matchesIn(RequirementCategory.EXPERIENCE).size();

        assertEquals(11, required, "the JD states 11 required skills once the experience band is separated");
        assertEquals(8, preferred, "the JD states 8 preferred skills");
        assertEquals(10, responsibilities, "the JD states 10 responsibilities");
        assertEquals(1, experience, "the experience band is stated twice and is one requirement");
    }

    @Test
    @DisplayName("Every match carries the job description line that created it")
    void everyMatchCarriesItsJobDescriptionEvidence() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD);

        for (RequirementMatch match : result.matches()) {
            assertFalse(match.jdEvidence().isEmpty(),
                    match.requirement() + " has no JD evidence, so the report cannot say what was asked for");
            assertNotNull(match.explanation());
            assertFalse(match.explanation().isBlank());
        }
    }

    @Test
    @DisplayName("An unreadable job description yields no score rather than a fabricated one")
    void anUnreadableJobDescriptionIsNotScored() {
        ScoredAnalysis result = analyze(AnalysisEngineFixture.ARJUN_RESUME,
                "About the role\n\nWe are a friendly team who like long walks and short standups.");

        assertTrue(result.matches().isEmpty(),
                "prose with no requirements must not become requirements");
        assertEquals(0, result.overallScore());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private ScoredAnalysis analyze(String resume, String jd) {
        AnalysisEngine.Result result = engine.analyze(resume, jd, null);
        assertNotNull(result);
        return result.scored();
    }

    private RequirementMatch find(ScoredAnalysis scored, String name) {
        Optional<RequirementMatch> match = scored.matches().stream()
                .filter(m -> m.requirement().equals(name))
                .findFirst();
        return match.orElseThrow(() -> new AssertionError(
                "no requirement named \"" + name + "\"; found: "
                        + scored.matches().stream().map(RequirementMatch::requirement).toList()));
    }

    private RequirementMatch required(ScoredAnalysis scored, String name) {
        RequirementMatch match = find(scored, name);
        assertEquals(RequirementCategory.REQUIRED_SKILL, match.category(),
                name + " should be a required skill");
        return match;
    }

    private void assertMatched(ScoredAnalysis scored, String name) {
        RequirementMatch match = find(scored, name);
        assertTrue(match.status() == MatchStatus.EXPLICIT_MATCH
                        || match.status() == MatchStatus.STRONG_CONTEXTUAL_MATCH,
                name + " should be matched for a candidate who uses it in production, got "
                        + match.status() + " with evidence " + match.resumeEvidence());
    }

    private void assertStatus(ScoredAnalysis scored, String name, MatchStatus expected) {
        assertEquals(expected, find(scored, name).status(), name + " has the wrong match state");
    }
}
