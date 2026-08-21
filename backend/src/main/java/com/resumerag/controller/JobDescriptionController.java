package com.resumerag.controller;

import com.resumerag.dto.JobDescriptionRequest;
import com.resumerag.dto.JobDescriptionResponse;
import com.resumerag.pipeline.RagPipelineOrchestrator;
import com.resumerag.repository.JobDescriptionRepository;
import com.resumerag.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/job-descriptions")
public class JobDescriptionController {

    private final RagPipelineOrchestrator ragPipelineOrchestrator;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final CurrentUser currentUser;

    public JobDescriptionController(RagPipelineOrchestrator ragPipelineOrchestrator,
                                     JobDescriptionRepository jobDescriptionRepository,
                                     CurrentUser currentUser) {
        this.ragPipelineOrchestrator = ragPipelineOrchestrator;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.currentUser = currentUser;
    }

    @PostMapping
    public JobDescriptionResponse create(@Valid @RequestBody JobDescriptionRequest request) {
        return JobDescriptionResponse.from(ragPipelineOrchestrator.saveJobDescription(currentUser.id(), request.title(), request.company(), request.rawText()));
    }

    @GetMapping
    public List<JobDescriptionResponse> list() {
        return jobDescriptionRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(JobDescriptionResponse::from)
                .toList();
    }
}
