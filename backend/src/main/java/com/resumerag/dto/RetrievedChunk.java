package com.resumerag.dto;

import java.util.Set;

public record RetrievedChunk(
        String content,
        String section,
        double similarity,
        double score,
        Set<String> matchedSkills
) {}
