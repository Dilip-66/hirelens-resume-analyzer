package com.resumerag.dto;

import java.util.UUID;

public record UploadResponse(
        UUID id,
        String fileName,
        int chunkCount,
        String message
) {}
