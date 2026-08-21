package com.resumerag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AnalysisRequest;
import com.resumerag.dto.AnalysisResponse;
import com.resumerag.model.Analysis;
import com.resumerag.repository.AnalysisRepository;
import com.resumerag.security.CurrentUser;
import com.resumerag.pipeline.RagPipelineOrchestrator;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final RagPipelineOrchestrator ragPipelineOrchestrator;
    private final AnalysisRepository analysisRepository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AnalysisController(RagPipelineOrchestrator ragPipelineOrchestrator,
                               AnalysisRepository analysisRepository,
                               CurrentUser currentUser) {
        this.ragPipelineOrchestrator = ragPipelineOrchestrator;
        this.analysisRepository = analysisRepository;
        this.currentUser = currentUser;
    }

    @PostMapping
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        Analysis analysis = ragPipelineOrchestrator.analyze(currentUser.id(), request.resumeId(), request.jobDescriptionId());
        return AnalysisResponse.from(analysis, objectMapper);
    }

    @GetMapping
    public List<AnalysisResponse> list() {
        return analysisRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id()).stream()
                .map(a -> AnalysisResponse.from(a, objectMapper))
                .toList();
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(@PathVariable java.util.UUID id) {
        Analysis a = analysisRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Analysis not found"));
        if (!a.getUserId().equals(currentUser.id())) {
            throw new SecurityException("Not authorized to view this analysis");
        }
        return AnalysisResponse.from(a, objectMapper);
    }
}
