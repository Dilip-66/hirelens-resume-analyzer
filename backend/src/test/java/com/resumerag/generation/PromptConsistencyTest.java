package com.resumerag.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AnalysisResult;
import com.resumerag.dto.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptConsistencyTest {

    private final ResponseParser parser = new ResponseParser(new ObjectMapper());
    private final PromptBuilder promptBuilder = new PromptBuilder();

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    private static RetrievedChunk chunk(String section, String content, double similarity) {
        return new RetrievedChunk(content, section, similarity, similarity, Set.of());
    }

    @Test
    void dropsAGapThatContradictsAMatchedSkill() {
        // This is the exact failure seen on a real resume: Terraform in the
        // matched list, and a gap claiming no infrastructure-as-code experience.
        String json = """
                {"matchScore": 92, "summary": "Strong fit",
                 "strengths": ["Java"], "gaps": ["No experience with Terraform or infrastructure as code"]}
                """;

        AnalysisResult result = parser.parse(json, set("Terraform", "Java"), set());

        assertTrue(result.gaps().isEmpty(),
                "a gap asserting absence of a matched skill should be dropped, got: " + result.gaps());
        assertTrue(result.matchedSkills().contains("Terraform"), "skill lists stay deterministic");
    }

    @Test
    void keepsAGapAboutAGenuinelyMissingSkill() {
        String json = """
                {"matchScore": 70, "summary": "Partial fit",
                 "strengths": ["Java"], "gaps": ["No experience with Kubernetes"]}
                """;

        AnalysisResult result = parser.parse(json, set("Java"), set("Kubernetes"));

        assertEquals(1, result.gaps().size());
        assertTrue(result.gaps().get(0).contains("Kubernetes"));
    }

    @Test
    void keepsANuancedGapThatMentionsAMatchedSkillWithoutAssertingAbsence() {
        // Word-boundary + absence-cue matching must not eat legitimate signal.
        String json = """
                {"matchScore": 75, "summary": "Good fit",
                 "strengths": ["Java"], "gaps": ["No Kubernetes at production scale"]}
                """;

        AnalysisResult result = parser.parse(json, set("Kubernetes", "Java"), set());

        assertEquals(1, result.gaps().size(),
                "a gap about scale/recency is real signal and should survive");
    }

    @Test
    void doesNotMatchASkillNameInsideALongerWord() {
        // "Go" must not be considered mentioned by "Going".
        String json = """
                {"matchScore": 70, "summary": "Partial fit",
                 "strengths": ["Java"], "gaps": ["No evidence of going-to-market work"]}
                """;

        AnalysisResult result = parser.parse(json, set("Go", "Java"), set());

        assertEquals(1, result.gaps().size());
    }

    @Test
    void dropsPlaceholderGapValues() {
        // Models answer "gaps" with the literal string "None" rather than [],
        // which the UI then renders as a gap literally named "None".
        String json = """
                {"matchScore": 96, "summary": "Strong fit",
                 "strengths": ["Java"], "gaps": ["None", "N/A", "No gaps"]}
                """;

        AnalysisResult result = parser.parse(json, set("Java"), set());

        assertTrue(result.gaps().isEmpty(), "placeholders should be dropped, got: " + result.gaps());
    }

    @Test
    void keepsRealGapsAlongsidePlaceholders() {
        String json = """
                {"matchScore": 60, "summary": "Partial fit",
                 "strengths": ["Java"], "gaps": ["None", "No Kubernetes experience listed"]}
                """;

        AnalysisResult result = parser.parse(json, set("Java"), set());

        assertEquals(1, result.gaps().size());
        assertTrue(result.gaps().get(0).contains("Kubernetes"));
    }

    @Test
    void promptDoesNotAskTheModelToRestateTheSkillLists() {
        String prompt = promptBuilder.buildUserPrompt(
                "Hiring a Java engineer", List.of(chunk("experience", "Built services", 0.9)),
                set("Java"), set("Java"), set());

        assertFalse(prompt.contains("\"matchedSkills\""),
                "matched skills are computed deterministically; asking the model to echo them is what "
                        + "produced contradictory output");
        assertFalse(prompt.contains("\"missingSkills\""),
                "missing skills are computed deterministically");
    }

    @Test
    void promptForbidsContradictingTheSkillScan() {
        String prompt = promptBuilder.buildUserPrompt(
                "Hiring a Java engineer", List.of(chunk("experience", "Built services", 0.9)),
                set("Terraform"), set("Terraform"), set());

        assertTrue(prompt.contains("never describe a matched skill as a gap"),
                "the prompt must state the consistency rule explicitly");
    }

    @Test
    void trimsLeastRelevantExcerptsToFitTheContextBudget() {
        List<RetrievedChunk> chunks = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) {
            chunks.add(chunk("experience", "x".repeat(800), 0.9 - i * 0.01));
        }

        // ~1.4k tokens of budget: far less than 30 x 800-char excerpts.
        String prompt = promptBuilder.buildUserPrompt(
                "Hiring a Java engineer", chunks, set("Java"), set("Java"), set(), 1400);

        int excerptCount = countOccurrences(prompt, "### Excerpt ");
        assertTrue(excerptCount < 30,
                "the prompt must not blow past the budget; it contained " + excerptCount + " excerpts");
        assertTrue(prompt.contains("### Excerpt 1"), "the most relevant excerpt must be kept");
    }

    @Test
    void unconstrainedBudgetKeepsEveryExcerpt() {
        List<RetrievedChunk> chunks = List.of(
                chunk("experience", "Built services", 0.9),
                chunk("projects", "Shipped a tool", 0.7),
                chunk("education", "BSc", 0.4));

        String prompt = promptBuilder.buildUserPrompt(
                "Hiring a Java engineer", chunks, set("Java"), set("Java"), set(), 0);

        assertEquals(3, countOccurrences(prompt, "### Excerpt "));
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }
}
