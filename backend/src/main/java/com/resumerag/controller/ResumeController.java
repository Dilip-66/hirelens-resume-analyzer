package com.resumerag.controller;

import com.resumerag.dto.ResumeResponse;
import com.resumerag.pipeline.RagPipelineOrchestrator;
import com.resumerag.repository.ResumeRepository;
import com.resumerag.security.CurrentUser;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final RagPipelineOrchestrator ragPipelineOrchestrator;
    private final ResumeRepository resumeRepository;
    private final CurrentUser currentUser;

    public ResumeController(RagPipelineOrchestrator ragPipelineOrchestrator, ResumeRepository resumeRepository, CurrentUser currentUser) {
        this.ragPipelineOrchestrator = ragPipelineOrchestrator;
        this.resumeRepository = resumeRepository;
        this.currentUser = currentUser;
    }

    @PostMapping(consumes = "multipart/form-data")
    public ResumeResponse upload(@RequestParam("file") MultipartFile file) {
        return ResumeResponse.from(ragPipelineOrchestrator.ingestResume(currentUser.id(), file));
    }

    @GetMapping
    public List<ResumeResponse> list() {
        return resumeRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(ResumeResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public ResumeResponse get(@PathVariable java.util.UUID id) {
        var r = resumeRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Resume not found"));
        if (!r.getUserId().equals(currentUser.id())) {
            throw new SecurityException("Not authorized to view this resume");
        }
        return ResumeResponse.from(r);
    }
}
