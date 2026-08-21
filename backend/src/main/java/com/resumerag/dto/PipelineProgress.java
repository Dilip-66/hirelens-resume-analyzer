package com.resumerag.dto;

public record PipelineProgress(
        String stage,
        String message,
        int progressPercent
) {}
