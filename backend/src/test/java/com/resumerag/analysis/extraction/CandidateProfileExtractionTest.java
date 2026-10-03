package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.CompoundOperator;
import com.resumerag.analysis.model.NormalizedRequirement;
import com.resumerag.analysis.model.Requirement;
import com.resumerag.analysis.model.RequirementCategory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * How a candidate-profile section is read.
 *
 * <p>The awkward case in a job description. Almost every full-stack posting ends
 * with a prose paragraph - "The ideal candidate should be able to understand
 * business requirements, design technical solutions, write reliable code, and work
 * across frontend and backend technologies" - and a line-oriented extractor has
 * three separate ways to get it wrong, all of which were live at once:
 *
 * <ul>
 *   <li>the <b>heading</b> ("What We're Looking For") is not a recognised header,
 *       so it fell through the line classifier and became a requirement in its own
 *       right - scored zero against every resume, reported as a gap, and handed
 *       back to the candidate as advice to "add a concrete line" for it;</li>
 *   <li>the <b>sentence</b> nests its connectors deeper than a fixed recursion
 *       depth allows, so the depth limit returned a fragment unsplit and the
 *       vocabulary then matched one word inside it and discarded the requirement
 *       that shared those words;</li>
 *   <li>the <b>concept</b> sat behind seven words of framing, which were baked
 *       into both the requirement's identity and the phrase it was matched on, so
 *       a resume answering it in the ordinary way could never match.</li>
 * </ul>
 */
class CandidateProfileExtractionTest {

    private final JobDescriptionExtractionService service = new JobDescriptionExtractionService(
            new RequirementNormalizationService(), new ExperienceRequirementParser(java.time.Clock.systemUTC()));

    private static final String PROFILE_JD = """
            Responsibilities:

            * Develop and maintain backend services using Java and Spring Boot.

            What We're Looking For:
            The ideal candidate should be able to understand business requirements, design technical solutions, write reliable code, troubleshoot problems, and work effectively across frontend and backend technologies.
            """;

    // ---------------------------------------------------------------------
    // The heading is not a requirement
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A candidate-profile heading is not itself a requirement")
    void aProfileHeadingIsNotARequirement() {
        List<Requirement> requirements = requirements(PROFILE_JD);

        assertTrue(requirements.stream().noneMatch(r -> r.name().toLowerCase().contains("looking for")),
                "a section heading is not something a candidate can evidence, so scoring it produces a "
                        + "permanent gap invented from the job description's own layout: "
                        + names(requirements));
    }

    @Test
    @DisplayName("A profile heading never reaches the gaps or the recommendations")
    void aProfileHeadingIsNotRecommended() {
        // Both lists are derived from the requirement list, so the check that
        // matters is end to end: no heading-shaped requirement exists, therefore
        // none can be reported as a gap or prescribed to the candidate.
        var engine = com.resumerag.analysis.AnalysisEngineFixture.engine();
        var result = engine.analyze(
                com.resumerag.analysis.AnalysisEngineFixture.ARJUN_RESUME, PROFILE_JD, null);

        assertTrue(result.gaps().stream().noneMatch(g -> g.toLowerCase().contains("looking for")),
                "a gap the candidate cannot close by editing their resume is a fabricated finding: "
                        + result.gaps());
        assertTrue(result.recommendations().stream().noneMatch(r -> r.toLowerCase().contains("looking for")),
                "\"add a concrete line for What We're Looking For\" is advice with no action behind it: "
                        + result.recommendations());
    }

    @Test
    @DisplayName("Every spelling of a profile heading is a header, not a requirement")
    void profileHeadingVariantsAreAllHeaders() {
        for (String heading : List.of(
                "What We're Looking For:", "What We Are Looking For:", "Who We're Looking For:",
                "The Ideal Candidate:", "Ideal Candidate:", "Candidate Profile:",
                "Person Specification:", "About the Candidate:")) {

            List<Requirement> requirements = requirements("""
                    Responsibilities:
                    * Ship features.

                    """ + heading + """
                    Debug issues and write reliable code.
                    """);

            assertTrue(requirements.stream().noneMatch(r -> r.name().toLowerCase().contains("looking")
                            || r.name().toLowerCase().contains("ideal candidate")
                            || r.name().toLowerCase().contains("candidate profile")
                            || r.name().toLowerCase().contains("person specification")),
                    "\"" + heading + "\" was extracted as a requirement");
        }
    }

    // ---------------------------------------------------------------------
    // The paragraph is decomposed, not swallowed
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("A profile sentence is read as its separate capabilities")
    void aProfileSentenceDecomposesIntoCapabilities() {
        Requirement profile = profileRequirement(PROFILE_JD);

        List<String> keys = profile.normalization().terms().stream()
                .map(t -> t.canonicalKey()).toList();

        assertTrue(keys.contains("PHRASE_UNDERSTAND_BUSINESS_REQUIREMENTS"),
                "business requirements is a capability the job description asks for: " + keys);
        assertTrue(keys.contains("PHRASE_RELIABLE_CODE"),
                "a nested comma list used to be truncated, and \"reliable code\" was silently discarded "
                        + "because the vocabulary matched \"troubleshoot\" in the same unsplit fragment: "
                        + keys);
        assertTrue(keys.contains("DEBUGGING"), "troubleshooting is one of the concepts: " + keys);
        assertTrue(keys.contains("FRONTEND") && keys.contains("BACKEND"),
                "the paragraph asks for working across both, and the user listed both as expected "
                        + "components: " + keys);
        assertEquals(CompoundOperator.AND, profile.normalization().operator(),
                "the job description lists all of them, so none of them is optional");
    }

    @Test
    @DisplayName("A profile sentence is not scored as one opaque skill")
    void aProfileSentenceIsNotOneOpaqueSkill() {
        Requirement profile = profileRequirement(PROFILE_JD);

        assertTrue(profile.normalization().terms().size() >= 5,
                "the paragraph names six distinct capabilities and collapsing them into a single term is what "
                        + "made the whole section report as one low-confidence finding: "
                        + profile.normalization().terms().size() + " term(s)");
    }

    @Test
    @DisplayName("A phrase requirement is matched on its content words, not just the whole sentence")
    void aPhraseRequirementMatchesItsContentWords() {
        Requirement profile = profileRequirement(PROFILE_JD);

        var businessRequirements = profile.normalization().terms().stream()
                .filter(t -> t.canonicalKey().equals("PHRASE_UNDERSTAND_BUSINESS_REQUIREMENTS"))
                .findFirst()
                .orElseThrow();

        assertTrue(businessRequirements.patterns().stream()
                        .anyMatch(p -> p.toLowerCase().contains("business requirements")
                                && !p.toLowerCase().contains("ideal candidate")),
                "a resume that writes \"gathered business requirements from stakeholders\" states the "
                        + "requirement plainly and contains none of the framing words, so a pattern built from "
                        + "the whole sentence can never fire: " + businessRequirements.patterns());
    }

    @Test
    @DisplayName("A profile sentence keeps the job description's own wording for its name")
    void aProfileSentenceKeepsTheEmployersWording() {
        Requirement profile = profileRequirement(PROFILE_JD);

        assertTrue(profile.sourceText().toLowerCase().contains("ideal candidate"),
                "the job description line is preserved verbatim, so the report can show what was actually "
                        + "asked for: " + profile.sourceText());
    }

    @Test
    @DisplayName("Deeply nested lists stay within a bounded number of terms")
    void aPathologicalConnectorChainIsStillBounded() {
        NormalizedRequirement normalized = new RequirementNormalizationService().normalize(
                "alpha, bravo, charlie, delta, echo, foxtrot, golf, hotel, india, juliet, kilo, lima");

        assertNotNull(normalized);
        assertTrue(normalized.terms().size() <= 8,
                "the guard against a pathological connector chain still has to hold, or a job description "
                        + "with a long list could manufacture a dozen phantom gaps: " + normalized.terms().size());
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private List<Requirement> requirements(String jd) {
        return service.extract(jd).requirements();
    }

    private Requirement profileRequirement(String jd) {
        return requirements(jd).stream()
                .filter(r -> r.sourceText().toLowerCase().contains("ideal candidate"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the profile sentence was not extracted; found: "
                        + names(requirements(jd))));
    }

    private List<String> names(List<Requirement> requirements) {
        return requirements.stream().map(Requirement::name).toList();
    }

    @Test
    @DisplayName("A profile paragraph is assessed as responsibilities, not required skills")
    void aProfileParagraphIsNotARequiredSkill() {
        Requirement profile = profileRequirement(PROFILE_JD);

        assertEquals(RequirementCategory.RESPONSIBILITY, profile.category(),
                "\"write reliable code\" and \"troubleshoot problems\" are duties; classifying them as required "
                        + "skills would let an unmet duty read as a missing skill");
        assertFalse(profile.normalization().terms().isEmpty());
    }
}