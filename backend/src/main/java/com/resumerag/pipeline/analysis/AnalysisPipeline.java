package com.resumerag.pipeline.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.dto.AnalysisResult;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.repository.AnalysisRepository;
import com.resumerag.repository.JobDescriptionRepository;
import com.resumerag.retrieval.RetrievalService;
import com.resumerag.generation.GenerationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class AnalysisPipeline {

    private final RetrievalService retrievalService;
    private final GenerationService generationService;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final AnalysisRepository analysisRepository;
    private final ObjectMapper objectMapper;

    public AnalysisPipeline(RetrievalService retrievalService,
                            GenerationService generationService,
                            JobDescriptionRepository jobDescriptionRepository,
                            AnalysisRepository analysisRepository,
                            ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.generationService = generationService;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.analysisRepository = analysisRepository;
        this.objectMapper = objectMapper;
    }

    public Analysis analyze(UUID userId, UUID resumeId, UUID jobDescriptionId) {
        JobDescription jd = jobDescriptionRepository.findById(jobDescriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Job description not found"));

        List<RetrievedChunk> retrievedChunks = retrievalService.retrieveRelevantChunks(resumeId, jd.getRawText());

        Set<String> requiredSkills = retrievalService.extractRequiredSkills(jd.getRawText());
        Set<String> matchedSkills = retrievedChunks.stream()
                .flatMap(c -> c.matchedSkills().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        AnalysisResult result = generationService.analyze(jd.getRawText(), retrievedChunks, requiredSkills, matchedSkills);

        Analysis analysis = new Analysis();
        analysis.setUserId(userId);
        analysis.setResumeId(resumeId);
        analysis.setJobDescriptionId(jobDescriptionId);
        analysis.setMatchScore(result.matchScore());
        analysis.setSummary(result.summary());
        try {
            analysis.setStrengthsJson(objectMapper.writeValueAsString(result.strengths()));
            analysis.setGapsJson(objectMapper.writeValueAsString(result.gaps()));
            analysis.setMatchedSkillsJson(objectMapper.writeValueAsString(result.matchedSkills()));
            analysis.setMissingSkillsJson(objectMapper.writeValueAsString(result.missingSkills()));
            analysis.setRetrievedChunksJson(objectMapper.writeValueAsString(retrievedChunks));
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize analysis result", e);
        }

        return analysisRepository.save(analysis);
    }
}
