package com.resumerag.dto;

import com.resumerag.model.Resume;

import java.time.Instant;
import java.util.UUID;

public record ResumeResponse(
        UUID id,
        UUID userId,
        String fileName,
        String rawText,
        Instant createdAt
) {
    public static ResumeResponse from(Resume resume) {
        return new ResumeResponse(
                resume.getId(),
                resume.getUserId(),
                resume.getFileName(),
                resume.getRawText(),
                resume.getCreatedAt()
        );
    }
}
