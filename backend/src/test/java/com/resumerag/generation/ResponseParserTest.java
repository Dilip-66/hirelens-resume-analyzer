package com.resumerag.generation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AnalysisResult;
import com.resumerag.exception.AnalysisFailedException;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponseParserTest {

    private final ResponseParser parser = new ResponseParser(new ObjectMapper());

    private static String scoreOnly(int score) {
        return "{\"matchScore\": " + score + ", \"summary\": \"Some fit\"}";
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }

    @Test
    void parsesACleanJsonResponse() {
        String json = """
                {
                  "matchScore": 82,
                  "summary": "Strong match on the backend stack.",
                  "strengths": ["Five years of Java", "Strong Postgres experience"],
                  "gaps": ["No Kubernetes exposure"]
                }
                """;

        AnalysisResult result = parser.parse(json, set("Java", "Postgres"), set("Kubernetes"));

        assertEquals(82, result.matchScore());
        assertEquals("Strong match on the backend stack.", result.summary());
        assertEquals(List.of("Five years of Java", "Strong Postgres experience"), result.strengths());
        assertEquals(List.of("No Kubernetes exposure"), result.gaps());
        assertEquals(List.of("Java", "Postgres"), result.matchedSkills());
        assertEquals(List.of("Kubernetes"), result.missingSkills());
    }

    @Test
    void stripsMarkdownCodeFences() {
        String json = """
                ```json
                {"matchScore": 70, "summary": "Decent fit", "strengths": [], "gaps": []}
                ```
                """;

        AnalysisResult result = parser.parse(json, set(), set());

        assertEquals(70, result.matchScore());
        assertEquals("Decent fit", result.summary());
    }

    @Test
    void recoversJsonSurroundedByProse() {
        String reply = "Here is my analysis:\n"
                + "{\"matchScore\": 55, \"summary\": \"Partial fit\", \"strengths\": [\"Python\"], \"gaps\": []}\n"
                + "Let me know if you need more detail.";

        AnalysisResult result = parser.parse(reply, set("Python"), set());

        assertEquals(55, result.matchScore());
        assertEquals("Partial fit", result.summary());
    }

    @Test
    void trieSkillsOverrideWhateverTheModelHallucinated() {
        String json = """
                {
                  "matchScore": 90,
                  "summary": "Great fit",
                  "strengths": [],
                  "gaps": [],
                  "matchedSkills": ["Rust", "Elixir"],
                  "missingSkills": []
                }
                """;

        // The model claims Rust/Elixir; the deterministic trie says Java only.
        AnalysisResult result = parser.parse(json, set("Java"), set("Rust"));

        assertEquals(List.of("Java"), result.matchedSkills());
        assertEquals(List.of("Rust"), result.missingSkills());
    }

    @Test
    void clampsScoresOutsideZeroToOneHundred() {
        assertEquals(100, parser.parse(scoreOnly(250), set(), set()).matchScore());
        assertEquals(0, parser.parse(scoreOnly(-40), set(), set()).matchScore());
    }

    @Test
    void fallsBackToOverallScoreWhenMatchScoreIsAbsent() {
        AnalysisResult result = parser.parse("{\"overallScore\": 64, \"summary\": \"Decent fit\"}", set(), set());

        assertEquals(64, result.matchScore());
    }

    @Test
    void failsLoudlyWhenSummaryIsMissing() {
        // A placeholder string reads like a real analysis. Better to surface the
        // failure than to show the user an empty verdict.
        assertThrows(AnalysisFailedException.class,
                () -> parser.parse("{\"matchScore\": 10}", set(), set()));
    }

    @Test
    void failsLoudlyWhenNoScoreFieldIsPresentAtAll() {
        // Defaulting to 0 is indistinguishable from a genuine 0% match, which for
        // a screening tool is a silent false rejection.
        assertThrows(AnalysisFailedException.class,
                () -> parser.parse("{}", set(), set()));
    }

    @Test
    void fallsBackFromGapsToWeaknesses() {
        String json = "{\"matchScore\": 60, \"summary\": \"Some fit\", \"gaps\": [], \"weaknesses\": [\"No leadership examples\"]}";

        assertEquals(List.of("No leadership examples"), parser.parse(json, set(), set()).gaps());
    }

    @Test
    void trimsAndDropsBlankListEntries() {
        String json = """
                {"matchScore": 60, "summary": "Some fit", "strengths": ["  Real strength  ", "   ", ""], "gaps": ["A gap"]}
                """;

        AnalysisResult result = parser.parse(json, set(), set());

        assertEquals(List.of("Real strength"), result.strengths());
    }

    @Test
    void toleratesNonArrayFields() {
        String json = "{\"matchScore\": 50, \"summary\": \"Some fit\", \"strengths\": \"not-an-array\", \"gaps\": 7}";

        AnalysisResult result = parser.parse(json, set(), set());

        assertTrue(result.strengths().isEmpty());
        assertTrue(result.gaps().isEmpty());
    }

    @Test
    void handlesNullSkillSets() {
        AnalysisResult result = parser.parse("{\"matchScore\": 50, \"summary\": \"Some fit\"}", null, null);

        assertNotNull(result.matchedSkills());
        assertTrue(result.matchedSkills().isEmpty());
        assertTrue(result.missingSkills().isEmpty());
    }

    @Test
    void rejectsAnEmptyResponse() {
        assertThrows(AnalysisFailedException.class, () -> parser.parse("", set(), set()));
        assertThrows(AnalysisFailedException.class, () -> parser.parse("   ", set(), set()));
        assertThrows(AnalysisFailedException.class, () -> parser.parse(null, set(), set()));
    }

    @Test
    void rejectsMalformedJson() {
        assertThrows(AnalysisFailedException.class,
                () -> parser.parse("this is definitely not json", set(), set()));
    }
}
