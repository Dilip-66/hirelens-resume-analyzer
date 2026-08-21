package com.resumerag.repository;

import com.resumerag.model.ResumeChunk;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ResumeChunkRepository extends JpaRepository<ResumeChunk, UUID> {
    List<ResumeChunk> findByResumeIdOrderByChunkIndexAsc(UUID resumeId);
}
