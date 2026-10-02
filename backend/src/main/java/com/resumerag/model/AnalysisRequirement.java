package com.resumerag.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * One requirement a job description stated, as extracted for one analysis.
 *
 * <p>Normalised storage rather than another JSON column on {@code analyses}. The
 * matched/missing skill lists that used to be the only record of what the analysis
 * looked at cannot answer "why was this required, or how load-bearing is it", and
 * an analysis nobody can audit is an analysis nobody can contest. One row per
 * requirement also makes the evidence chain queryable - a report's claim can be
 * traced to the sentence and the job description line behind it.
 *
 * <p>Row per analysis, cascading with its parent: the JD a requirement came from
 * is versioned by the analysis that read it, so a re-run after a JD edit produces
 * a new set rather than mutating the record of an earlier judgement.
 */
@Entity
@Table(name = "analysis_requirements",
        indexes = @Index(name = "idx_analysis_requirements_analysis", columnList = "analysis_id"))
public class AnalysisRequirement {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "analysis_id", nullable = false)
    private UUID analysisId;

    /** Ordinal within the analysis, so requirement 7 is the same one on re-read. */
    @Column(name = "requirement_index", nullable = false)
    private int requirementIndex;

    /** Canonical display name, e.g. "Java" or "AWS or Cloud platforms". */
    @Column(name = "name", nullable = false)
    private String name;

    /** Stable identity across the JD's wording, e.g. ANY_OF_AWS_CLOUD_PLATFORM. */
    @Column(name = "normalized_name", nullable = false)
    private String normalizedName;

    @Column(name = "category", nullable = false)
    private String category;

    @Column(name = "importance", nullable = false)
    private String importance;

    /** How much capability the JD demanded: BASIC, PROFESSIONAL, ADVANCED. */
    @Column(name = "demand", nullable = false)
    private String demand;

    /** The job description line, verbatim. */
    @Column(name = "source_text", nullable = false)
    private String sourceText;

    /** Accepted surface forms, JSON array. */
    @Column(name = "synonyms_json")
    private String synonymsJson;

    /** False when the engine inferred the requirement rather than reading it. */
    @Column(name = "explicit_requirement", nullable = false)
    private boolean explicitRequirement;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public AnalysisRequirement() {}

    public AnalysisRequirement(UUID analysisId, int requirementIndex, String name, String normalizedName,
                               String category, String importance, String demand, String sourceText,
                               String synonymsJson, boolean explicitRequirement) {
        this.analysisId = analysisId;
        this.requirementIndex = requirementIndex;
        this.name = name;
        this.normalizedName = normalizedName;
        this.category = category;
        this.importance = importance;
        this.demand = demand;
        this.sourceText = sourceText;
        this.synonymsJson = synonymsJson;
        this.explicitRequirement = explicitRequirement;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public UUID getAnalysisId() { return analysisId; }
    public void setAnalysisId(UUID analysisId) { this.analysisId = analysisId; }
    public int getRequirementIndex() { return requirementIndex; }
    public void setRequirementIndex(int requirementIndex) { this.requirementIndex = requirementIndex; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String normalizedName) { this.normalizedName = normalizedName; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getImportance() { return importance; }
    public void setImportance(String importance) { this.importance = importance; }
    public String getDemand() { return demand; }
    public void setDemand(String demand) { this.demand = demand; }
    public String getSourceText() { return sourceText; }
    public void setSourceText(String sourceText) { this.sourceText = sourceText; }
    public String getSynonymsJson() { return synonymsJson; }
    public void setSynonymsJson(String synonymsJson) { this.synonymsJson = synonymsJson; }
    public boolean isExplicitRequirement() { return explicitRequirement; }
    public void setExplicitRequirement(boolean explicitRequirement) { this.explicitRequirement = explicitRequirement; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
