package com.resumerag.repository;

import com.resumerag.model.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface JobDescriptionRepository extends JpaRepository<JobDescription, UUID> {
    List<JobDescription> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
