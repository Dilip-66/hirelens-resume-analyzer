package com.resumerag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.resumerag.analysis.AnalysisEngine;
import com.resumerag.analysis.AnalysisEngineFixture;
import com.resumerag.analysis.model.AnalysisReportMeta;
import com.resumerag.analysis.model.ExperienceAlignment;
import com.resumerag.analysis.model.MatchLabel;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementImportance;
import com.resumerag.analysis.model.ScoreCategory;
import com.resumerag.dto.analysis.AnalysisDetailAssembler;
import com.resumerag.dto.analysis.AnalysisDetailResponse;
import com.resumerag.dto.analysis.CategoryScoreResponse;
import com.resumerag.dto.analysis.ExperienceAlignmentResponse;
import com.resumerag.dto.analysis.RequirementMatchResponse;
import com.resumerag.dto.analysis.ScoreBreakdownResponse;
import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.model.Resume;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The API contract, held to the promise that the richer analysis is additive.
 *
 * <p>An existing report must still deserialise, an existing client must still
 * find every field it reads, and an analysis produced before this change must
 * still render. Those are the three ways an "improved" analysis pipeline usually
 * breaks production: a removed field, a renamed one, or a new non-null field that
 * an older client cannot handle.
 */
class AnalysisResponseCompatibilityTest {

    /**
     * Mirrors Spring Boot's auto-configured mapper: the JavaTimeModule is what lets
     * an {@code Instant} round-trip, and without it the compatibility assertions
     * would fail on serialisation rather than on the contract they exist to check.
     */
    private final ObjectMapper mapper = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .build();
    private final AnalysisEngine engine = AnalysisEngineFixture.engine();

    @Test
    @DisplayName("Every field the old contract carried is still present and unchanged")
    void theOriginalContractIsIntact() throws Exception {
        Analysis analysis = legacyAnalysis();
        AnalysisResponse response = AnalysisResponse.from(analysis, null, null, mapper);

        String json = mapper.writeValueAsString(response);

        // Names, in the original order and with the original types. A client
        // written against the previous response must find all of them.
        assertTrue(json.contains("\"id\""), json);
        assertTrue(json.contains("\"resumeId\""), json);
        assertTrue(json.contains("\"jobDescriptionId\""), json);
        assertTrue(json.contains("\"candidateName\""), json);
        assertTrue(json.contains("\"resumeFileName\""), json);
        assertTrue(json.contains("\"jobTitle\""), json);
        assertTrue(json.contains("\"jobCompany\""), json);
        assertTrue(json.contains("\"matchScore\""), json);
        assertTrue(json.contains("\"summary\""), json);
        assertTrue(json.contains("\"strengths\""), json);
        assertTrue(json.contains("\"gaps\""), json);
        assertTrue(json.contains("\"matchedSkills\""), json);
        assertTrue(json.contains("\"missingSkills\""), json);
        assertTrue(json.contains("\"createdAt\""), json);

        // matchScore is still an integer. Widening it, or making it nullable,
        // would break a client that renders it directly.
        assertTrue(json.contains("\"matchScore\":87"), json);
    }

    @Test
    @DisplayName("A report analysed before this change still deserialises, with no detail")
    void anOldReportStillDeserialises() throws Exception {
        AnalysisResponse response = AnalysisResponse.from(legacyAnalysis(), null, null, mapper);

        // The old shape is the one a pre-upgrade database row produces, and the
        // shorter constructor is what an old caller would use.
        assertNull(response.detail());
        assertNull(response.recommendations());
        assertEquals(87, response.matchScore());

        // And it round-trips, so a client that has cached an old response is not
        // holding something unparseable.
        String json = mapper.writeValueAsString(response);
        AnalysisResponse parsed = mapper.readValue(json, AnalysisResponse.class);
        assertEquals(87, parsed.matchScore());
        assertNotNull(parsed.strengths());
    }

    @Test
    @DisplayName("An old report rendered as JSON carries null detail, not a broken object")
    void anOldReportSerialisesCleanly() throws Exception {
        String json = mapper.writeValueAsString(AnalysisResponse.from(legacyAnalysis(), null, null, mapper));

        assertTrue(json.contains("\"detail\":null"), json);
        assertTrue(json.contains("\"recommendations\":null"), json);
    }

    @Test
    @DisplayName("The new fields sit alongside the old ones, not in place of them")
    void theNewFieldsSitAlongsideTheOldOnes() throws Exception {
        AnalysisEngine.Result result =
                engine.analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD, null);
        AnalysisDetailResponse detail = new AnalysisDetailAssembler().from(result, result.recommendations());

        Analysis analysis = new Analysis();
        analysis.setId(UUID.randomUUID());
        analysis.setUserId(UUID.randomUUID());
        analysis.setResumeId(UUID.randomUUID());
        analysis.setJobDescriptionId(UUID.randomUUID());
        analysis.setMatchScore(result.scored().overallScore());
        analysis.setSummary("summary");
        analysis.setStrengthsJson("[\"s\"]");
        analysis.setGapsJson("[\"g\"]");
        analysis.setMatchedSkillsJson("[\"Java\"]");
        analysis.setMissingSkillsJson("[\"AI/ML\"]");
        analysis.setCreatedAt(Instant.now());

        AnalysisResponse response = new AnalysisResponse(
                analysis.getId(), analysis.getResumeId(), analysis.getJobDescriptionId(), "Arjun Rao",
                "arjun.pdf", "Full-Stack Software Developer", "TechNova", analysis.getMatchScore(),
                analysis.getSummary(), List.of("s"), List.of("g"), List.of("Java"), List.of("AI/ML"),
                analysis.getCreatedAt(), detail, detail.recommendations());

        String json = mapper.writeValueAsString(response);

        assertTrue(json.contains("\"matchScore\""), "the legacy score field must remain");
        assertTrue(json.contains("\"matchedSkills\""), "the legacy skill lists must remain");
        assertTrue(json.contains("\"missingSkills\""), json);
        assertTrue(json.contains("\"detail\""), json);
        assertTrue(json.contains("\"scoreBreakdown\""), json);
        assertTrue(json.contains("\"requirements\""), json);
        assertTrue(json.contains("\"experienceAlignment\""), json);
    }

    @Test
    @DisplayName("An unassessed dimension serialises as null, not as zero")
    void anUnassessedDimensionSerialisesAsNull() throws Exception {
        // Projects and education stay null for this pair: the resume describes no
        // projects and the job description asks for no degree. ATS is measured,
        // because text quality can be assessed from any resume.
        AnalysisEngine.Result result =
                engine.analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD, null);
        AnalysisDetailResponse detail = new AnalysisDetailAssembler().from(result, result.recommendations());

        String json = mapper.writeValueAsString(detail.scoreBreakdown());

        assertTrue(json.contains("\"projects\":null"), json);
        assertTrue(json.contains("\"education\":null"), json);
        assertFalse(json.contains("\"projects\":0"), "a zero would read as a measurement");
        assertFalse(json.contains("\"education\":0"), "a zero would read as a measurement");
        assertTrue(json.contains("\"ats\":"), json);
        assertTrue(json.contains("\"requiredSkills\":"), json);
    }

    @Test
    @DisplayName("Every requirement in the detail carries its evidence and its reason")
    void everyRequirementCarriesItsEvidence() {
        AnalysisEngine.Result result =
                engine.analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD, null);
        AnalysisDetailResponse detail = new AnalysisDetailAssembler().from(result, result.recommendations());

        assertEquals(30, detail.requirements().size());
        for (RequirementMatchResponse requirement : detail.requirements()) {
            assertNotNull(requirement.status());
            assertNotNull(requirement.explanation());
            assertFalse(requirement.jdEvidence().isEmpty(),
                    requirement.name() + " has no JD evidence to show the user");
        }

        RequirementMatchResponse docker = detail.requirements().stream()
                .filter(r -> r.name().equals("Docker"))
                .findFirst().orElseThrow();
        assertEquals(RequirementCategory.REQUIRED_SKILL, docker.category());
        assertEquals(RequirementImportance.HIGH, docker.importance());
        assertEquals(4, docker.evidenceStrength());
        assertTrue(docker.confidence() > 0.9);
        assertTrue(docker.resumeEvidence().stream().anyMatch(e -> e.contains("Dockerized")));
    }

    @Test
    @DisplayName("The experience alignment serialises the fields the contract names")
    void theExperienceAlignmentSerialises() throws Exception {
        AnalysisEngine.Result result =
                engine.analyze(AnalysisEngineFixture.ARJUN_RESUME, AnalysisEngineFixture.FULL_STACK_JD, null);
        ExperienceAlignmentResponse experience =
                new AnalysisDetailAssembler().from(result, result.recommendations()).experienceAlignment();

        assertNotNull(experience);
        assertEquals(2.5, experience.candidateYears());
        assertEquals(1.0, experience.requiredMinYears());
        assertEquals(3.0, experience.requiredMaxYears());
        assertEquals(ExperienceAlignment.Status.WITHIN_RANGE, experience.status());
        assertEquals(100.0, experience.score());

        String json = mapper.writeValueAsString(experience);
        for (String field : List.of("candidateYears", "requiredMinYears", "requiredMaxYears", "status", "score")) {
            assertTrue(json.contains("\"" + field + "\""), "missing " + field + " in " + json);
        }
    }

    @Test
    @DisplayName("An unscoreable analysis is flagged, not reported as a 0% match")
    void anUnscoreableAnalysisIsFlagged() {
        AnalysisEngine.Result result = engine.analyze(
                AnalysisEngineFixture.ARJUN_RESUME, "About the role\n\nWe like long walks.", null);
        AnalysisDetailResponse detail = new AnalysisDetailAssembler().from(result, result.recommendations());

        assertFalse(detail.scored(), "a report that could not be measured must say so");
        assertNull(detail.overallScore(),
                "a 0% overall score would be a false rejection of the candidate");
    }

    @Test
    @DisplayName("The stored report header round-trips")
    void theStoredReportHeaderRoundTrips() throws Exception {
        ExperienceAlignment alignment = new ExperienceAlignment(2.5, true, 1.0, 3.0,
                ExperienceAlignment.Status.WITHIN_RANGE, 100.0, "inside the range");
        AnalysisReportMeta meta = new AnalysisReportMeta(87, true, "Strong Match", 0.85, alignment,
                List.of("Add a line for code review."));

        String json = mapper.writeValueAsString(meta);
        AnalysisReportMeta parsed = mapper.readValue(json, AnalysisReportMeta.class);

        assertEquals(87, parsed.overallScore());
        assertTrue(parsed.scored());
        assertEquals("Strong Match", parsed.matchLabel());
        assertEquals(2.5, parsed.experienceAlignment().candidateYears());
        assertEquals(ExperienceAlignment.Status.WITHIN_RANGE, parsed.experienceAlignment().status());
        assertEquals(1, parsed.recommendations().size());
    }

    @Test
    @DisplayName("The candidate name still falls back for a report with no stored name")
    void candidateNameFallbacksAreUnchanged() {
        Resume resume = new Resume();
        resume.setFileName("arjun.pdf");
        resume.setRawText("Arjun Rao\nSoftware Developer...");

        AnalysisResponse response = AnalysisResponse.from(legacyAnalysis(), resume, null, mapper);
        assertEquals("Arjun Rao", response.candidateName());
    }

    private Analysis legacyAnalysis() {
        Analysis analysis = new Analysis();
        analysis.setId(UUID.randomUUID());
        analysis.setUserId(UUID.randomUUID());
        analysis.setResumeId(UUID.randomUUID());
        analysis.setJobDescriptionId(UUID.randomUUID());
        analysis.setMatchScore(87);
        analysis.setSummary("Strong match on the backend stack.");
        analysis.setStrengthsJson("[\"Java\"]");
        analysis.setGapsJson("[\"AI/ML - not explicitly mentioned in the resume\"]");
        analysis.setMatchedSkillsJson("[\"Java\",\"Docker\"]");
        analysis.setMissingSkillsJson("[\"AI/ML\"]");
        analysis.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
        return analysis;
    }
}
