package com.resumerag.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AnalysisRequest;
import com.resumerag.dto.AnalysisResponse;
import com.resumerag.dto.analysis.AnalysisDetailResponse;
import com.resumerag.exception.ResourceNotFoundException;
import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.model.Resume;
import com.resumerag.repository.AnalysisRepository;
import com.resumerag.repository.JobDescriptionRepository;
import com.resumerag.repository.ResumeRepository;
import com.resumerag.security.CurrentUser;
import com.resumerag.pipeline.RagPipelineOrchestrator;
import com.resumerag.pipeline.analysis.AnalysisDetailReader;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final RagPipelineOrchestrator ragPipelineOrchestrator;
    private final AnalysisRepository analysisRepository;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final CurrentUser currentUser;
    private final ObjectMapper objectMapper;
    private final AnalysisDetailReader detailReader;

    public AnalysisController(RagPipelineOrchestrator ragPipelineOrchestrator,
                              AnalysisRepository analysisRepository,
                              ResumeRepository resumeRepository,
                              JobDescriptionRepository jobDescriptionRepository,
                              CurrentUser currentUser,
                              ObjectMapper objectMapper,
                              AnalysisDetailReader detailReader) {
        this.ragPipelineOrchestrator = ragPipelineOrchestrator;
        this.analysisRepository = analysisRepository;
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.currentUser = currentUser;
        this.objectMapper = objectMapper;
        this.detailReader = detailReader;
    }

    @PostMapping
    public AnalysisResponse analyze(@Valid @RequestBody AnalysisRequest request) {
        UUID userId = currentUser.id();
        Analysis analysis = ragPipelineOrchestrator.analyze(userId, request.resumeId(), request.jobDescriptionId());
        return withDetail(AnalysisResponse.from(analysis,
                resumeRepository.findById(request.resumeId()).orElse(null),
                jobDescriptionRepository.findById(request.jobDescriptionId()).orElse(null),
                objectMapper), analysis);
    }

    @GetMapping
    public List<AnalysisResponse> list() {
        List<Analysis> analyses = analysisRepository.findByUserIdOrderByCreatedAtDesc(currentUser.id());
        return decorate(analyses);
    }

    @GetMapping("/{id}")
    public AnalysisResponse get(@PathVariable java.util.UUID id) {
        Analysis a = analysisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Analysis not found"));
        if (!a.getUserId().equals(currentUser.id())) {
            // Report as "not found" rather than "forbidden" so the endpoint can't
            // be used to probe which analysis ids exist.
            throw new ResourceNotFoundException("Analysis not found");
        }
        return withDetail(AnalysisResponse.from(a,
                resumeRepository.findById(a.getResumeId()).orElse(null),
                jobDescriptionRepository.findById(a.getJobDescriptionId()).orElse(null),
                objectMapper), a);
    }

    /**
     * Attaches the evidence chain when the analysis has one.
     *
     * <p>Null for a report analysed before evidence-based scoring existed, and
     * left null deliberately: the client then renders the report exactly as it
     * always did. A detail object full of nulls would read as a failure rather
     * than as an older report, and the flat fields above it are still correct.
     */
    private AnalysisResponse withDetail(AnalysisResponse response, Analysis analysis) {
        AnalysisDetailResponse detail = detailReader.read(analysis);
        if (detail == null) {
            return response;
        }
        return new AnalysisResponse(
                response.id(), response.resumeId(), response.jobDescriptionId(),
                response.candidateName(), response.resumeFileName(), response.jobTitle(),
                response.jobCompany(), response.matchScore(), response.summary(),
                response.strengths(), response.gaps(), response.matchedSkills(),
                response.missingSkills(), response.createdAt(), detail, detail.recommendations());
    }

    /**
     * Resolves each report's resume and job description for display.
     *
     * <p>Deliberately batched: resolving them one row at a time would issue
     * 2 extra queries per report, so a 50-row history page would run 101 queries.
     * Two keyed lookups keep it at three regardless of list length.
     *
     * <p>Also deliberately does NOT attach {@code detail}. Loading the evidence
     * chain for every row on a history page would add two more queries per report
     * - a hundred and fifty for fifty rows - to return data the list never shows.
     * So a list response carries {@code detail: null} for every report, including
     * new ones, and the field must be read from the single-report endpoint.
     *
     * <p>That is a real asymmetry in the contract rather than something a client
     * should have to infer, so it is stated here: the list is for identity, score
     * and summary; the detail endpoint is for the explanation.
     */
    private List<AnalysisResponse> decorate(List<Analysis> analyses) {
        if (analyses.isEmpty()) {
            return List.of();
        }
        Map<UUID, Resume> resumes = new HashMap<>();
        for (Resume r : resumeRepository.findAllById(analyses.stream()
                .map(Analysis::getResumeId).collect(Collectors.toSet()))) {
            resumes.put(r.getId(), r);
        }
        Map<UUID, JobDescription> jobs = new HashMap<>();
        for (JobDescription j : jobDescriptionRepository.findAllById(analyses.stream()
                .map(Analysis::getJobDescriptionId).collect(Collectors.toSet()))) {
            jobs.put(j.getId(), j);
        }
        return analyses.stream()
                .map(a -> AnalysisResponse.from(a,
                        resumes.get(a.getResumeId()),
                        jobs.get(a.getJobDescriptionId()),
                        objectMapper))
                .toList();
    }
}
