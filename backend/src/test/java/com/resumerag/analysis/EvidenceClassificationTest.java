package com.resumerag.analysis;

import com.resumerag.analysis.evidence.SemanticEvidenceMatcher;
import com.resumerag.analysis.model.MatchStatus;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.analysis.model.ScoreCategory;
import com.resumerag.analysis.model.ScoredAnalysis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The evidence taxonomy: demonstrated, supported, partial, or not mentioned.
 *
 * <p><b>Why these are separate from {@link AnalysisEngineRegressionTest}.</b> That
 * suite asks "does the reference candidate score the way it should". This one asks
 * the question that was actually broken: <em>given this evidence, what is the
 * honest match state?</em> Every case here is a rule about evidence quality, and
 * none of them is a rule about a particular candidate.
 *
 * <p><b>On the reference pair.</b> Nothing in this class pins a score. A scoring
 * bug changes a classification, and a classification test catches it whether or not
 * the headline number happens to move - which is the property the reference-pair
 * test cannot give on its own.
 *
 * <p><b>On Ollama.</b> Several cases run the same analysis twice, once with a
 * semantic index returning a weakly-similar passage for every requirement and once
 * with none at all, and assert the classification is the same either way. That is
 * the whole point: a real embedding index returns its nearest chunks whether or not
 * they are relevant, so if the score moves with Ollama up or down, the model is
 * deciding the verdict.
 */
class EvidenceClassificationTest {

    // ---------------------------------------------------------------------
    // The four tiers
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A technology named in professional work is demonstrated")
    void professionalUseIsDemonstrated() {
        RequirementMatch java = required(
                analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD), "Java");

        assertEquals(MatchStatus.EXPLICIT_MATCH, java.status());
        assertEquals(4, java.evidenceStrength());
        assertEquals(1.0, java.confidence());
    }

    @Test
    @DisplayName("Indirect evidence is supported, never demonstrated")
    void indirectEvidenceIsSupported() {
        // The resume never says "software development lifecycle". It says CI/CD,
        // testing workflows and automated deployment, which is real evidence about
        // the lifecycle and is not the same as naming it.
        RequirementMatch sdlc = required(
                analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD),
                "Software development lifecycle");

        assertEquals(MatchStatus.STRONG_CONTEXTUAL_MATCH, sdlc.status(),
                "CI/CD, automated testing and deployment support the lifecycle without naming it");
        assertFalse(sdlc.explanation().toLowerCase().contains("demonstrat"),
                "support must not be reported as demonstration: " + sdlc.explanation());
    }

    @Test
    @DisplayName("A requirement with one supported half and one silent half is partial")
    void halfSupportedGroupIsPartial() {
        // "Debugging and application performance": the 30% response-time
        // improvement speaks to performance and says nothing about debugging.
        RequirementMatch debugging = find(
                analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD),
                "Debugging + Application performance");

        assertEquals(MatchStatus.PARTIAL_MATCH, debugging.status(),
                "performance is supported and debugging is silent, so the conjunction is only partly met");
        assertTrue(!debugging.resumeEvidence().isEmpty(),
                "a partial match must still say what it based that on");
    }

    @Test
    @DisplayName("A requirement the resume never mentions is silence, with no evidence attached")
    void noEvidenceIsSilence() {
        RequirementMatch codeReview = find(
                analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD),
                "Code reviews");

        assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, codeReview.status());
        assertEquals(0.0, codeReview.confidence());
        assertEquals(0, codeReview.evidenceStrength());
        assertTrue(codeReview.resumeEvidence().isEmpty(),
                "no evidence must be reported as no evidence, not as a weak example: "
                        + codeReview.resumeEvidence());
    }

    // ---------------------------------------------------------------------
    // The defect: a compound requirement with no evidence became partial
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A compound requirement with nothing evidenced is silence, not partial credit")
    void anUnevidencedCompoundIsNotPartial() {
        // The reported bug. "Code reviews and software development activities" names
        // two things the resume mentions neither of, and used to be reported as
        // PARTIAL at 41% confidence - not because anything in the resume supported
        // it, but because the requirement contained the word "and".
        String jd = """
                Senior Engineer

                Responsibilities:
                * Code reviews and software development activities
                """;

        for (SemanticEvidenceMatcher matcher : new SemanticEvidenceMatcher[] { null, weakSemanticIndex(0.64) }) {
            AnalysisEngine engine = matcher == null
                    ? AnalysisEngineFixture.engine()
                    : AnalysisEngineFixture.engine(matcher);

            RequirementMatch match = find(engine.analyze(AnalysisEngineFixture.ARJUN_RESUME, jd, null).scored(),
                    "Code reviews + software development activities");

            assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, match.status(),
                    "a conjunction with no evidence is silence, whether or not the semantic index has "
                            + "something to return");
            assertEquals(0.0, match.confidence(),
                    "partial credit for an unevidenced requirement is credit for the requirement's shape, "
                            + "not for the resume");
            assertTrue(match.resumeEvidence().isEmpty());
        }
    }

    @Test
    @DisplayName("A single-term requirement is equally unmoved by a noisy semantic index")
    void aNoisySemanticIndexCannotCreateAMatch() {
        String jd = """
                Senior Engineer

                Responsibilities:
                * Mentor junior developers
                * Participate in code reviews
                """;

        ScoredAnalysis withNoise = AnalysisEngineFixture.engine(weakSemanticIndex(0.64))
                .analyze(AnalysisEngineFixture.ARJUN_RESUME, jd, null).scored();

        for (RequirementMatch match : withNoise.matches()) {
            assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, match.status(),
                    "a 0.64-similarity passage is adjacent at best and cannot support a claim: "
                            + match.requirement());
            assertEquals(0.0, match.confidence());
        }
    }

    @Test
    @DisplayName("The score does not move when the semantic index starts returning noise")
    void theScoreIsIndependentOfSemanticNoise() {
        String jd = AnalysisEngineFixture.FULL_STACK_JD;
        String resume = AnalysisEngineFixture.ARJUN_RESUME;

        ScoredAnalysis lexical = AnalysisEngineFixture.engine()
                .analyze(resume, jd, null).scored();
        ScoredAnalysis noisy = AnalysisEngineFixture.engine(weakSemanticIndex(0.64))
                .analyze(resume, jd, null).scored();

        assertEquals(lexical.overallScore(), noisy.overallScore(),
                "the same resume and job description must score the same whether or not a local embedding "
                        + "model is running");
        for (ScoreCategory category : ScoreCategory.values()) {
            assertEquals(lexical.category(category).score(), noisy.category(category).score(),
                    "category " + category + " moved when the semantic layer returned noise");
        }
    }

    // ---------------------------------------------------------------------
    // Conjunction and disjunction
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A disjunction is satisfied by its first evidenced term")
    void aDisjunctionIsSatisfiedByEitherTerm() {
        String jd = """
                Frontend Engineer

                Required Skills:
                * Knowledge of JavaScript and/or TypeScript
                """;

        RequirementMatch onlyJavaScript = required(analyze("""
                Sam Reed

                Technical Skills:
                * JavaScript

                Experience:
                Frontend Developer - Kite | 2023-Present
                * Built interactive interfaces in JavaScript.
                """, jd), "JavaScript or TypeScript");

        assertEquals(MatchStatus.EXPLICIT_MATCH, onlyJavaScript.status(),
                "the job description said one of the two was enough, so meeting one of them meets the "
                        + "requirement - downgrading it under-claims the candidate");
    }

    @Test
    @DisplayName("A conjunction still requires every term")
    void aConjunctionStillRequiresEveryTerm() {
        String jd = """
                Backend Engineer

                Required Skills:
                * Familiarity with Git and GitHub
                """;

        RequirementMatch gitOnly = required(analyze("""
                Dana Brooks

                Technical Skills:
                * Git

                Experience:
                Backend Engineer - Helix | 2022-Present
                * Versioned services with Git.
                """, jd), "Git + GitHub");

        assertEquals(MatchStatus.PARTIAL_MATCH, gitOnly.status(),
                "\"and\" means both, and half of one is a partial finding");
        assertTrue(gitOnly.confidence() < 0.5,
                "partial compound confidence is scaled by how much was found: " + gitOnly.confidence());
    }

    @Test
    @DisplayName("A disjunction is never described as partly evidenced")
    void aDisjunctionIsNotDescribedAsPartial() {
        RequirementMatch disjunction = find(
                analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD),
                "JavaScript or TypeScript");

        assertFalse(disjunction.explanation().contains("of 2 parts"),
                "the job description asked for JavaScript and/or TypeScript, so a count of unmet parts "
                        + "would be a statement about the requirement, not the resume: "
                        + disjunction.explanation());
    }

    // ---------------------------------------------------------------------
    // The inferences the engine must not make
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("Git and GitHub alone do not evidence code review")
    void versionControlAloneIsNotCodeReview() {
        // A: a full version-control and CI/CD stack, and nothing that says anyone
        // reviewed anyone's code. Reviewing other people's work is a different
        // capability from committing your own, and sharing a vocabulary with it -
        // "pull requests", "branches", "repository" - is not evidence of it.
        String jd = """
                Backend Engineer

                Responsibilities:
                * Participate in code reviews
                """;

        RequirementMatch codeReview = find(analyze("""
                Arjun Rao

                Technical Skills:
                * Git
                * GitHub
                * GitHub Actions
                * CI/CD

                Experience:
                Software Developer - TechNova | 2024-Present
                * Implemented GitHub Actions CI/CD pipelines across twelve repositories and managed
                  branches and pull requests.
                * Dockerized microservices and deployed applications on AWS EC2 and S3.
                """, jd), "Code reviews");

        assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, codeReview.status(),
                "git, GitHub, pull requests and CI/CD are a delivery stack, not evidence of reviewing "
                        + "other people's code");
        assertEquals(0.0, codeReview.confidence());
        assertTrue(codeReview.resumeEvidence().isEmpty());
    }

    @Test
    @DisplayName("Being a software developer does not evidence code review")
    void softwareEmploymentAloneIsNotCodeReview() {
        // B: the strongest possible "they write software" evidence - a title, a
        // summary and a body of shipped work - and no code review.
        String jd = """
                Senior Engineer

                Responsibilities:
                * Participate in code reviews
                * Mentor junior developers
                """;

        RequirementMatch codeReview = find(analyze("""
                Robin Ellis

                Summary:
                Software Developer with 4 years of experience building and shipping software.

                Experience:
                Software Developer - Ashwood | 2022-Present
                * Developed and shipped features across Java and Spring Boot services.
                * Built a React and TypeScript dashboard used by the whole company.
                * Reduced API response time by 30% through caching and query optimization.
                """, jd), "Code reviews");

        assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, codeReview.status(),
                "a software developer's entire employment is not evidence of reviewing code, and the "
                        + "career framing of the resume must not leak into a duty the resume is silent about");
    }

    @Test
    @DisplayName("Performance evidence supports the performance half and not the debugging half")
    void performanceEvidenceDoesNotProveDebugging() {
        // E: the requirement is split by the job description's own "and", and the
        // resume answers exactly one side of it.
        String jd = """
                Backend Engineer

                Responsibilities:
                * Debug issues and improve application performance
                """;

        RequirementMatch both = find(analyze(AnalysisEngineFixture.ARJUN_RESUME, jd),
                "Debugging + Application performance");

        assertEquals(MatchStatus.PARTIAL_MATCH, both.status(),
                "the 30% response-time improvement is real evidence about performance and none about "
                        + "debugging");
        assertFalse(both.explanation().contains("0 of 2"),
                "the explanation must not claim nothing was found: " + both.explanation());

        // The resume names debugging outright but only demonstrates performance
        // through a quantified result, so the conjunction is half-named. That is
        // the honest reading: the bullet does say "Debugged", and it does not say
        // "application performance".
        RequirementMatch halfNamed = find(analyze("""
                Robin Ellis

                Experience:
                Software Engineer - Ashwood | 2022-Present
                * Debugged production incidents and reduced API response time by 30%.
                """, jd), "Debugging + Application performance");

        assertEquals(MatchStatus.PARTIAL_MATCH, halfNamed.status(),
                "one half named and one half demonstrated is still a partly-met conjunction");

        // Naming both halves completes it.
        RequirementMatch bothNamed = find(analyze("""
                Robin Ellis

                Experience:
                Software Engineer - Ashwood | 2022-Present
                * Debugged production incidents while improving application performance.
                """, jd), "Debugging + Application performance");

        assertEquals(MatchStatus.EXPLICIT_MATCH, bothNamed.status(),
                "a conjunction whose halves are both named must not be held at partial - that is the "
                        + "under-claiming the operator fix removed");
    }

    @Test
    @DisplayName("Python alone does not evidence AI or machine learning")
    void pythonAloneIsNotMachineLearning() {
        RequirementMatch ai = find(
                analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD),
                "Artificial intelligence or Machine learning");

        assertEquals(MatchStatus.NOT_EXPLICITLY_MENTIONED, ai.status(),
                "a general-purpose language is not a machine learning practice");
    }

    @Test
    @DisplayName("Being a software developer does not evidence a software development lifecycle")
    void jobTitleAloneIsNotAnSdlc() {
        String jd = """
                Engineer

                Required Skills:
                * Understanding of software development lifecycle
                """;
        String resume = """
                Alex Turner

                Summary:
                Software Developer building things.

                Experience:
                Developer - Acme | 2023-Present
                * Shipped features.
                """;

        RequirementMatch sdlc = required(analyze(resume, jd), "Software development lifecycle");
        assertNotExplicit(sdlc);
    }

    @Test
    @DisplayName("A generic summary sentence does not evidence clean code")
    void genericProseIsNotCleanCode() {
        String jd = """
                Engineer

                Responsibilities:
                * Write clean, maintainable, and scalable code
                """;
        // "scalable" describes the applications, not the code, and "Software
        // Developer" is the job title. Neither is a statement about code quality.
        String resume = """
                Robin Ellis

                Summary:
                Software Developer building scalable full-stack web applications.

                Experience:
                Developer - Ashwood | 2022-Present
                * Shipped billing features.
                """;

        RequirementMatch cleanCode = find(analyze(resume, jd), "Clean, maintainable code");
        assertNotExplicit(cleanCode);
    }

    @Test
    @DisplayName("Real clean-code practice is still recognised")
    void genuineCleanCodePracticeIsStillRecognised() {
        String jd = """
                Engineer

                Responsibilities:
                * Write clean, maintainable, and scalable code
                """;
        String resume = """
                Robin Ellis

                Experience:
                Developer - Ashwood | 2022-Present
                * Refactored the legacy billing module into reusable components and documented each
                  design pattern.
                """;

        RequirementMatch cleanCode = find(analyze(resume, jd), "Clean, maintainable code");
        assertTrue(cleanCode.status() == MatchStatus.EXPLICIT_MATCH
                        || cleanCode.status() == MatchStatus.STRONG_CONTEXTUAL_MATCH,
                "refactoring into reusable components and documenting design patterns is real evidence of "
                        + "maintainable code, and tightening the vocabulary must not have lost it: got "
                        + cleanCode.status());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void assertNotExplicit(RequirementMatch match) {
        assertTrue(match.status() == MatchStatus.NOT_EXPLICITLY_MENTIONED
                        || match.status() == MatchStatus.PARTIAL_MATCH
                        || match.status() == MatchStatus.STRONG_CONTEXTUAL_MATCH,
                "must not be reported as demonstrated; got " + match.status() + " for " + match.requirement());
        assertFalse(match.status() == MatchStatus.EXPLICIT_MATCH,
                match.requirement() + " was reported as demonstrated on evidence that does not name it: "
                        + match.resumeEvidence());
    }

    /**
     * Stands in for a real embedding index, which returns its nearest chunks
     * whether or not they are relevant.
     *
     * <p>The similarity sits deliberately inside the band the engine calls
     * "adjacent at best" - above the threshold at which a passage is discarded as
     * irrelevant, below the one at which it counts as contextual.
     */
    private static SemanticEvidenceMatcher weakSemanticIndex(double similarity) {
        return new SemanticEvidenceMatcher() {
            @Override
            public Index indexFor(UUID resumeId) {
                return new Index() {
                    @Override
                    public List<List<Hit>> rankAll(List<String> requirementTexts, int limit) {
                        return requirementTexts.stream()
                                .map(text -> List.of(new Hit(
                                        "Software Developer with 2.5 years of experience building "
                                                + "scalable full-stack web applications",
                                        "summary", similarity)))
                                .toList();
                    }

                    @Override
                    public List<Hit> rank(String requirementText, int limit) {
                        return List.of();
                    }
                };
            }
        };
    }

    private ScoredAnalysis analyze(String resume, String jd) {
        return AnalysisEngineFixture.engine().analyze(resume, jd, null).scored();
    }

    private RequirementMatch find(ScoredAnalysis scored, String name) {
        return scored.matches().stream()
                .filter(m -> m.requirement().equals(name))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no requirement named \"" + name + "\"; found: "
                        + scored.matches().stream().map(RequirementMatch::requirement).toList()));
    }

    private RequirementMatch required(ScoredAnalysis scored, String name) {
        RequirementMatch match = find(scored, name);
        assertEquals(com.resumerag.analysis.model.RequirementCategory.REQUIRED_SKILL, match.category(),
                name + " should be a required skill");
        return match;
    }
}