package com.resumerag.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * How one resume was found to measure up to one requirement, with the evidence.
 *
 * <p>Child of {@link AnalysisRequirement}, not of the analysis directly: the match
 * has no meaning without the requirement it answers, and splitting the link makes
 * it impossible to store a match whose requirement is absent.
 *
 * <p>Evidence arrays are stored as JSON text, matching how {@code analyses} already
 * stores its lists. They are read as a block and always written together, so
 * normalising them into rows would add joins without adding a query anyone makes.
 */
@Entity
@Table(name = "analysis_matches",
        indexes = @Index(name = "idx_analysis_matches_requirement",
                columnList = "analysis_requirement_id"))
public class AnalysisMatch {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "analysis_requirement_id", nullable = false)
    private UUID analysisRequirementId;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    /** Evidence quality 0-4, where 4 means used in professional work. */
    @Column(name = "evidence_strength", nullable = false)
    private int evidenceStrength;

    /** EXPLICIT, INFERRED or NOT_EXPLICIT - how the connection was drawn. */
    @Column(name = "provenance", nullable = false)
    private String provenance;

    /** The resume sentences that decided the outcome, JSON array. */
    @Column(name = "resume_evidence_json")
    private String resumeEvidenceJson;

    /** The job description lines this requirement came from, JSON array. */
    @Column(name = "jd_evidence_json")
    private String jdEvidenceJson;

    /** One sentence, in plain language, saying why. */
    @Column(name = "explanation")
    private String explanation;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AnalysisMatch() {}

    public AnalysisMatch(UUID analysisRequirementId, String status, double confidence, int evidenceStrength,
                         String provenance, String resumeEvidenceJson, String jdEvidenceJson,
                         String explanation) {
        this.analysisRequirementId = analysisRequirementId;
        this.status = status;
        this.confidence = confidence;
        this.evidenceStrength = evidenceStrength;
        this.provenance = provenance;
        this.resumeEvidenceJson = resumeEvidenceJson;
        this.jdEvidenceJson = jdEvidenceJson;
        this.explanation = explanation;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAnalysisRequirementId() { return analysisRequirementId; }
    public void setAnalysisRequirementId(UUID analysisRequirementId) { this.analysisRequirementId = analysisRequirementId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public int getEvidenceStrength() { return evidenceStrength; }
    public void setEvidenceStrength(int evidenceStrength) { this.evidenceStrength = evidenceStrength; }
    public String getProvenance() { return provenance; }
    public void setProvenance(String provenance) { this.provenance = provenance; }
    public String getResumeEvidenceJson() { return resumeEvidenceJson; }
    public void setResumeEvidenceJson(String resumeEvidenceJson) { this.resumeEvidenceJson = resumeEvidenceJson; }
    public String getJdEvidenceJson() { return jdEvidenceJson; }
    public void setJdEvidenceJson(String jdEvidenceJson) { this.jdEvidenceJson = jdEvidenceJson; }
    public String getExplanation() { return explanation; }
    public void setExplanation(String explanation) { this.explanation = explanation; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
