package com.resumerag.dto;

import com.resumerag.model.JobDescription;

import java.time.Instant;
import java.util.UUID;

public record JobDescriptionResponse(
        UUID id,
        UUID userId,
        String title,
        String company,
        String rawText,
        Instant createdAt
) {
    public static JobDescriptionResponse from(JobDescription jd) {
        return new JobDescriptionResponse(
                jd.getId(),
                jd.getUserId(),
                jd.getTitle(),
                jd.getCompany(),
                jd.getRawText(),
                jd.getCreatedAt()
        );
    }
}
