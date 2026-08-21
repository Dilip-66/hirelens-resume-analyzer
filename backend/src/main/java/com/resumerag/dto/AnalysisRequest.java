package com.resumerag.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AnalysisRequest(
        @NotNull(message = "resumeId is required") UUID resumeId,
        @NotNull(message = "jobDescriptionId is required") UUID jobDescriptionId
) {}
