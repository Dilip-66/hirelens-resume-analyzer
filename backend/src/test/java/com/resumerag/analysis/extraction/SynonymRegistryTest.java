package com.resumerag.analysis.extraction;

import com.resumerag.analysis.model.CompoundOperator;
import com.resumerag.analysis.model.NormalizedRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a phrase of job-description wording means.
 *
 * <p>Every test here corresponds to a way the benchmark showed the engine was
 * reading job descriptions optimistically. The pattern is the same throughout:
 * a job description that names a specific product, or two products where one
 * implies the other, was being read as if it had named the broader family.
 */
class SynonymRegistryTest {

    @Test
    @DisplayName("A product name is read as the product, not as its vendor")
    void aSpecificProductIsNotWidenedToItsVendor() {
        assertEquals(List.of("EC2"), SynonymRegistry.canonicalKeysForRequirement("AWS EC2"));

        assertFalse(SynonymRegistry.canonicalKeysForRequirement("AWS EC2").contains("AWS"),
                "a role naming EC2 is not a role anyone with 'AWS' on a CV has met; the whole point of "
                        + "naming a service is that you use that service");
    }

    @Test
    @DisplayName("Naming an AWS service does not by itself claim the AWS platform")
    void aServiceNameIsNotReadAsThePlatform() {
        assertEquals(List.of("S3"), SynonymRegistry.canonicalKeysForRequirement("Amazon S3"));

        // Known limitation, recorded in CALIBRATION.md: a CV listing only "S3"
        // does not credit AWS on evidence alone. The engine reads the platform when
        // the CV says so, or credits it partially through the cloud-platform path,
        // rather than inferring a platform from a product name - the same rule that
        // stops "AWS EC2" being widened to "AWS" in the other direction. Adding the
        // inference would need its own relation, because React Native must not imply
        // React and a blanket "add the parent" rule would reintroduce that bug.
        assertFalse(SynonymRegistry.canonicalKeysIn("Amazon S3").contains("AWS"),
                "inferred platform credit would need an explicit service-to-platform relation, "
                        + "not a rule that adds every parent term");
    }

    @Test
    @DisplayName("Two siblings in a disjunction are two requirements, not one")
    void aDisjunctionOfSiblingsStaysTwoRequirements() {
        assertEquals(List.of("EC2", "S3"),
                SynonymRegistry.canonicalKeysForRequirement("AWS EC2 or AWS S3"),
                "'or' means either will do, and each is a separate thing to have done");
    }

    @Test
    @DisplayName("React Native is not React")
    void reactNativeIsNotReact() {
        assertEquals(List.of("REACT_NATIVE"), SynonymRegistry.canonicalKeysForRequirement("React Native"));

        assertFalse(SynonymRegistry.canonicalKeysIn("Built a React Native app").contains("REACT"),
                "shipping a mobile app is not evidence of web frontend work, which is what a React "
                        + "requirement is asking for");
    }

    @Test
    @DisplayName("Machine learning is not deep learning")
    void machineLearningIsNotDeepLearning() {
        assertEquals(List.of("DEEP_LEARNING"), SynonymRegistry.canonicalKeysForRequirement("Deep Learning"));
        assertFalse(SynonymRegistry.canonicalKeysIn("Applied deep learning to ranking").contains("MACHINE_LEARNING"),
                "these are different bodies of work and the benchmark showed the conflation was "
                        + "flattering strong ML candidates");
    }

    @Test
    @DisplayName("PostgreSQL experience still counts as SQL experience")
    void aSpecificDatabaseStillCountsAsTheGeneralSkill() {
        assertTrue(SynonymRegistry.canonicalKeysIn("Designed PostgreSQL schemas and tuned SQL queries")
                        .contains("SQL"),
                "the letters really are inside the word, but the two are separate facts and the line "
                        + "names both");
    }

    @Test
    @DisplayName("A two-word phrase is read whole rather than split into its parts")
    void aCompoundPhraseIsNotSplitIntoItsParts() {
        assertEquals(List.of("REST_API"), SynonymRegistry.canonicalKeysForRequirement("RESTful API development"));

        assertFalse(SynonymRegistry.canonicalKeysForRequirement("RESTful API development").contains("API_DESIGN"),
                "'API development' overlaps the phrase but starts later, and taking it would lose the "
                        + "REST half that the rest of the job description also asks for");
    }

    @Test
    @DisplayName("A word is not read as a technology hiding inside a longer word")
    void aTechnologyIsNotFoundInsideALongerWord() {
        assertFalse(SynonymRegistry.canonicalKeysIn("Wrote TypeScript all week").contains("JAVA"),
                "'java' is the first four letters of 'typescript', and matching it there would credit "
                        + "backend experience to every frontend candidate");
    }

    @Test
    @DisplayName("'Service' in a phrase does not make it microservices")
    void theWordServiceIsNotMicroservices() {
        assertFalse(SynonymRegistry.canonicalKeysIn("Built a service boundary for the payments domain")
                        .contains("MICROSERVICES"),
                "a service is not an architecture, and treating it as one rewards ordinary backend work");
    }

    @Test
    @DisplayName("A disjunction written without spaces is still a disjunction")
    void anUnspacedDisjunctionStillSplits() {
        // Splitting is the normaliser's job, not the registry's: it reads the
        // separator and hands each side over separately. Testing it here would test
        // a layer that is not the one which decides.
        RequirementNormalizationService normalizer = new RequirementNormalizationService();

        NormalizedRequirement ai = normalizer.normalize("Basic knowledge of AI/ML");

        assertEquals(2, ai.terms().size(),
                "'AI/ML' names two things, so either should be enough to meet it");
        assertEquals(com.resumerag.analysis.model.CompoundOperator.OR, ai.operator(),
                "'/' means either will do, not both");
    }

    @Test
    @DisplayName("A slash between two acronyms is not a disjunction")
    void anUnspacedSlashBetweenAcronymsIsOneThing() {
        assertEquals(List.of("CI_CD"), SynonymRegistry.canonicalKeysForRequirement("CI/CD"),
                "continuous integration and delivery is one named practice, not a choice between 'CI' and 'CD'");
    }
}