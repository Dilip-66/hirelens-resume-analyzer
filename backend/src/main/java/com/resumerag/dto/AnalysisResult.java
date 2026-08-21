package com.resumerag.dto;

import java.util.List;

public record AnalysisResult(
        int matchScore,
        String summary,
        List<String> strengths,
        List<String> gaps,
        List<String> matchedSkills,
        List<String> missingSkills
) {}
