package com.resumerag.pipeline;

import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.model.Resume;
import com.resumerag.pipeline.analysis.AnalysisPipeline;
import com.resumerag.pipeline.ingestion.IngestionPipeline;
import com.resumerag.repository.JobDescriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Service
public class RagPipelineOrchestrator {

    private final IngestionPipeline ingestionPipeline;
    private final AnalysisPipeline analysisPipeline;
    private final JobDescriptionRepository jobDescriptionRepository;

    public RagPipelineOrchestrator(IngestionPipeline ingestionPipeline,
                                   AnalysisPipeline analysisPipeline,
                                   JobDescriptionRepository jobDescriptionRepository) {
        this.ingestionPipeline = ingestionPipeline;
        this.analysisPipeline = analysisPipeline;
        this.jobDescriptionRepository = jobDescriptionRepository;
    }

    public Resume ingestResume(UUID userId, MultipartFile file) {
        return ingestionPipeline.ingest(userId, file);
    }

    public Analysis analyze(UUID userId, UUID resumeId, UUID jobDescriptionId) {
        return analysisPipeline.analyze(userId, resumeId, jobDescriptionId);
    }

    public JobDescription saveJobDescription(UUID userId, String title, String company, String rawText) {
        JobDescription jd = new JobDescription();
        jd.setUserId(userId);
        jd.setTitle(title);
        jd.setCompany(company);
        jd.setRawText(rawText);
        return jobDescriptionRepository.save(jd);
    }
}
