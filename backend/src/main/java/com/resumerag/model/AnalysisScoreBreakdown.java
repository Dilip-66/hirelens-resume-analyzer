package com.resumerag.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One dimension's contribution to an analysis score.
 *
 * <p>A row per category, including the ones that were <em>not</em> scored.
 * Persisting the unassessed ones is the point: "projects was not assessed" is a
 * fact about the analysis, and storing only the numbers that exist would leave no
 * way to tell a genuine 80% for projects from a dimension that was never measured.
 * {@code score} is nullable and {@code note} says why.
 */
@Entity
@Table(name = "analysis_score_breakdowns",
        indexes = @Index(name = "idx_analysis_score_breakdowns_analysis", columnList = "analysis_id"))
public class AnalysisScoreBreakdown {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    @Column(name = "category", nullable = false)
    private String category;

    /** Null when the dimension could not be assessed. Never faked. */
    @Column(name = "score")
    private Double score;

    @Column(name = "weight", nullable = false)
    private double weight;

    /** The dimension's contribution after weighting; null when not assessed. */
    @Column(name = "weighted_score")
    private Double weightedScore;

    @Column(name = "requirements_considered", nullable = false)
    private int requirementsConsidered;

    /** Why this dimension is or is not scored. */
    @Column(name = "note")
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AnalysisScoreBreakdown() {}

    public AnalysisScoreBreakdown(UUID analysisId, String category, Double score, double weight,
                                  Double weightedScore, int requirementsConsidered, String note) {
        this.analysisId = analysisId;
        this.category = category;
        this.score = score;
        this.weight = weight;
        this.weightedScore = weightedScore;
        this.requirementsConsidered = requirementsConsidered;
        this.note = note;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAnalysisId() { return analysisId; }
    public void setAnalysisId(UUID analysisId) { this.analysisId = analysisId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Double getScore() { return score; }
    public void setScore(Double score) { this.score = score; }
    public double getWeight() { return weight; }
    public void setWeight(double weight) { this.weight = weight; }
    public Double getWeightedScore() { return weightedScore; }
    public void setWeightedScore(Double weightedScore) { this.weightedScore = weightedScore; }
    public int getRequirementsConsidered() { return requirementsConsidered; }
    public void setRequirementsConsidered(int requirementsConsidered) { this.requirementsConsidered = requirementsConsidered; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
