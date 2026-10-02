package com.resumerag.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "resumes")
public class Resume {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "raw_text", nullable = false)
    private String rawText;

    /**
     * Display name for the candidate, so an analysis report can identify whose
     * resume it is about. Nullable because rows created before this column
     * existed have no value, and the report falls back to the file name.
     */
    @Column(name = "candidate_name")
    private String candidateName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Resume() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getRawText() { return rawText; }
    public void setRawText(String rawText) { this.rawText = rawText; }
    public String getCandidateName() { return candidateName; }
    public void setCandidateName(String candidateName) { this.candidateName = candidateName; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
