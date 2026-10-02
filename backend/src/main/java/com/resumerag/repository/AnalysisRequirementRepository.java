package com.resumerag.repository;

import com.resumerag.model.AnalysisRequirement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRequirementRepository extends JpaRepository<AnalysisRequirement, UUID> {

    /** Requirements for one analysis, in the order they were extracted. */
    List<AnalysisRequirement> findByAnalysisIdOrderByRequirementIndexAsc(UUID analysisId);
}
