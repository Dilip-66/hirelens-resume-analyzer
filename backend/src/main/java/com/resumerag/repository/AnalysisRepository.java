package com.resumerag.repository;

import com.resumerag.model.Analysis;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AnalysisRepository extends JpaRepository<Analysis, UUID> {
    List<Analysis> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
