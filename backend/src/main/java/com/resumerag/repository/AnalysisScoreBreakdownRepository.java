package com.resumerag.repository;

import com.resumerag.model.AnalysisScoreBreakdown;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisScoreBreakdownRepository extends JpaRepository<AnalysisScoreBreakdown, UUID> {

    /** Score dimensions for one analysis, including the ones left unassessed. */
    List<AnalysisScoreBreakdown> findByAnalysisIdOrderByIdAsc(UUID analysisId);
}
