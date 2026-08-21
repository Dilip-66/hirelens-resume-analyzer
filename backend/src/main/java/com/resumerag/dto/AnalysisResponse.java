package com.resumerag.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.model.Analysis;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResponse(
        UUID id,
        UUID resumeId,
        UUID jobDescriptionId,
        int matchScore,
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<String> matchedSkills,
        List<String> missingSkills,
        Instant createdAt
) {
    public static AnalysisResponse from(Analysis a, ObjectMapper mapper) {
        try {
            return new AnalysisResponse(
                    a.getId(), a.getResumeId(), a.getJobDescriptionId(), a.getMatchScore(), a.getSummary(),
                    List.of(mapper.readValue(a.getStrengthsJson(), String[].class)),
                    List.of(mapper.readValue(a.getGapsJson(), String[].class)),
                    List.of(mapper.readValue(a.getMatchedSkillsJson(), String[].class)),
                    List.of(mapper.readValue(a.getMissingSkillsJson(), String[].class)),
                    a.getCreatedAt()
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize analysis JSON fields", e);
        }
    }
}
