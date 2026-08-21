package com.resumerag.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "analyses")
public class Analysis {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "resume_id", nullable = false)
    private UUID resumeId;

    @Column(name = "job_description_id", nullable = false)
    private UUID jobDescriptionId;

    @Column(name = "match_score", nullable = false)
    private int matchScore;

    @Lob
    @Column(name = "summary")
    private String summary;

    /** JSON-encoded string arrays, kept as raw JSON text to avoid a jsonb Hibernate type dependency. */
    @Lob
    @Column(name = "strengths_json")
    private String strengthsJson;

    @Lob
    @Column(name = "gaps_json")
    private String gapsJson;

    @Lob
    @Column(name = "matched_skills_json")
    private String matchedSkillsJson;

    @Lob
    @Column(name = "missing_skills_json")
    private String missingSkillsJson;

    @Lob
    @Column(name = "retrieved_chunks_json")
    private String retrievedChunksJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public UUID getResumeId() { return resumeId; }
    public void setResumeId(UUID resumeId) { this.resumeId = resumeId; }
    public UUID getJobDescriptionId() { return jobDescriptionId; }
    public void setJobDescriptionId(UUID jobDescriptionId) { this.jobDescriptionId = jobDescriptionId; }
    public int getMatchScore() { return matchScore; }
    public void setMatchScore(int matchScore) { this.matchScore = matchScore; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getStrengthsJson() { return strengthsJson; }
    public void setStrengthsJson(String strengthsJson) { this.strengthsJson = strengthsJson; }
    public String getGapsJson() { return gapsJson; }
    public void setGapsJson(String gapsJson) { this.gapsJson = gapsJson; }
    public String getMatchedSkillsJson() { return matchedSkillsJson; }
    public void setMatchedSkillsJson(String matchedSkillsJson) { this.matchedSkillsJson = matchedSkillsJson; }
    public String getMissingSkillsJson() { return missingSkillsJson; }
    public void setMissingSkillsJson(String missingSkillsJson) { this.missingSkillsJson = missingSkillsJson; }
    public String getRetrievedChunksJson() { return retrievedChunksJson; }
    public void setRetrievedChunksJson(String retrievedChunksJson) { this.retrievedChunksJson = retrievedChunksJson; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
