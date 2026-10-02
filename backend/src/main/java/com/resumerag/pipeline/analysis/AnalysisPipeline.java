package com.resumerag.pipeline.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumerag.analysis.AnalysisEngine;
import com.resumerag.analysis.model.AnalysisNarrative;
import com.resumerag.analysis.model.RequirementCategory;
import com.resumerag.analysis.model.RequirementMatch;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.exception.ResourceNotFoundException;
import com.resumerag.generation.GenerationService;
import com.resumerag.generation.NarrativeValidator;
import com.resumerag.generation.PromptBuilder;
import com.resumerag.model.Analysis;
import com.resumerag.model.JobDescription;
import com.resumerag.model.Resume;
import com.resumerag.repository.AnalysisRepository;
import com.resumerag.repository.JobDescriptionRepository;
import com.resumerag.repository.ResumeRepository;
import com.resumerag.retrieval.RetrievalService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Orchestrates one analysis end to end.
 *
 * <p>Deliberately NOT annotated {@code @Transactional} at the class level.
 *
 * <p>A class-level transaction opens a pooled connection on the first read and
 * holds it until the method returns - which here means across the entire LLM
 * call, up to OLLAMA_CHAT_TIMEOUT_SECONDS. With a 5-connection Hikari pool that
 * lets five concurrent analyses starve every other request in the app, including
 * plain health checks. The individual writes wrap themselves in their own
 * transactions, so nothing is lost by keeping the scope narrow.
 *
 * <p>The order matters and is the point of the rewrite:
 *
 * <ol>
 *   <li>Retrieval, unchanged, still RAG over the resume's own chunks.</li>
 *   <li>The deterministic engine scores the match from evidence.</li>
 *   <li>The model writes the prose, and is allowed to fail.</li>
 *   <li>The report is persisted with the score from step 2.</li>
 * </ol>
 *
 * <p>Previously steps 2 and 3 were one call to the model, so the score was
 * whatever the model returned - unauditable, and free to contradict the skill scan
 * printed in the same prompt.
 */
@Service
public class AnalysisPipeline {

    private static final Logger log = LoggerFactory.getLogger(AnalysisPipeline.class);

    private final RetrievalService retrievalService;
    private final GenerationService generationService;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final ResumeRepository resumeRepository;
    private final AnalysisRepository analysisRepository;
    private final AnalysisEngine analysisEngine;
    private final AnalysisDetailWriter detailWriter;
    private final NarrativeValidator narrativeValidator;
    private final ObjectMapper objectMapper;

    public AnalysisPipeline(RetrievalService retrievalService,
                            GenerationService generationService,
                            JobDescriptionRepository jobDescriptionRepository,
                            ResumeRepository resumeRepository,
                            AnalysisRepository analysisRepository,
                            AnalysisEngine analysisEngine,
                            AnalysisDetailWriter detailWriter,
                            NarrativeValidator narrativeValidator,
                            ObjectMapper objectMapper) {
        this.retrievalService = retrievalService;
        this.generationService = generationService;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.resumeRepository = resumeRepository;
        this.analysisRepository = analysisRepository;
        this.analysisEngine = analysisEngine;
        this.detailWriter = detailWriter;
        this.narrativeValidator = narrativeValidator;
        this.objectMapper = objectMapper;
    }

    public Analysis analyze(UUID userId, UUID resumeId, UUID jobDescriptionId) {
        JobDescription jd = jobDescriptionRepository.findById(jobDescriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Job description not found"));
        if (!userId.equals(jd.getUserId())) {
            throw new ResourceNotFoundException("Job description not found");
        }
        if (jd.getRawText() == null || jd.getRawText().isBlank()) {
            throw new IllegalArgumentException("This job description has no text to analyze against.");
        }

        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));
        if (!userId.equals(resume.getUserId())) {
            throw new ResourceNotFoundException("Resume not found");
        }

        // --- 1. The existing RAG retrieval, untouched. Feeds the narrative prompt
        // and the stored excerpts, so the AI assistant and the audit trail keep
        // working exactly as they did.
        List<RetrievedChunk> retrievedChunks = retrievalService.retrieveRelevantChunks(
                resumeId, jd.getRawText(), legacyRequiredSkills(jd.getRawText()));

        // --- 2. The deterministic engine. No model, no database writes, and the
        // only thing that decides the score.
        AnalysisEngine.Result result = analysisEngine.analyze(resume.getRawText(), jd.getRawText(), resumeId);

        // --- 3. Prose only, never load-bearing, and checked against step 2 before
        // anything is stored. A model asked for strengths and gaps will restate a
        // job description line as a strength unless its output is validated, which
        // NarrativeValidator does.
        AnalysisNarrative narrative = narrate(jd.getRawText(), retrievedChunks, result);

        // --- 4. Persist, with the score from step 2.
        Analysis analysis = new Analysis();
        analysis.setUserId(userId);
        analysis.setResumeId(resumeId);
        analysis.setJobDescriptionId(jobDescriptionId);
        analysis.setMatchScore(result.scored().overallScore());
        analysis.setSummary(narrative.summary());
        analysis.setStrengthsJson(writeJson(narrative.strengths()));
        analysis.setGapsJson(writeJson(narrative.gaps()));
        analysis.setMatchedSkillsJson(writeJson(legacyMatchedSkills(result)));
        analysis.setMissingSkillsJson(writeJson(legacyMissingSkills(result)));
        analysis.setRetrievedChunksJson(writeJson(retrievedChunks));

        Analysis saved = analysisRepository.save(analysis);
        return detailWriter.write(saved, result);
    }

    /**
     * Asks the model to write the report's prose, and survives it not answering.
     *
     * <p>Returns the engine's deterministic fallback on any failure - timeout,
     * connection refused, malformed JSON, a model that loaded mid-request. That is
     * the whole benefit of moving the score out of the model: an unreachable LLM
     * now costs a report its polish instead of costing the user their analysis.
     */
    private AnalysisNarrative narrate(String jdText, List<RetrievedChunk> chunks,
                                      AnalysisEngine.Result result) {
        if (result.scored().matches().isEmpty()) {
            return result.toNarrative();
        }
        try {
            AnalysisNarrative narrative = generationService.narrate(
                    jdText, chunks, narrativeRequirements(result));
            if (narrative.summary() == null || narrative.summary().isBlank()) {
                log.warn("The analysis model returned no summary; using the deterministic one instead.");
                return result.toNarrative();
            }
            return narrativeValidator.validate(narrative, result.scored().matches(),
                    result.strengths(), result.gaps());
        } catch (Exception e) {
            log.warn("The analysis model could not write this report's summary; using the deterministic "
                    + "one instead. The score is unaffected: {}", e.getMessage());
            return result.toNarrative();
        }
    }

    private List<PromptBuilder.RequirementSummary> narrativeRequirements(AnalysisEngine.Result result) {
        return result.scored().matches().stream()
                .map(m -> new PromptBuilder.RequirementSummary(
                        m.requirement(), m.category().name(), m.status().name(), m.resumeEvidence()))
                .toList();
    }

    /**
     * The skills the legacy prompt still needs, from the same trie scan as before.
     *
     * <p>Kept so the retrieval prompt and the AI assistant keep seeing the skill
     * set they were built around.
     */
    private Set<String> legacyRequiredSkills(String jdText) {
        return retrievalService.extractRequiredSkills(jdText);
    }

    /**
     * {@code matchedSkills} for the original API field: requirements the engine
     * found evidence for.
     *
     * <p>Deliberately includes partial and contextual matches. A field called
     * "matched skills" that silently drops every requirement the candidate only
     * partially evidenced would tell a frontend that a documented strength is
     * absent, and the existing UI renders this list as the left column of a
     * side-by-side comparison.
     */
    private List<String> legacyMatchedSkills(AnalysisEngine.Result result) {
        return requiredSkillNames(result, false);
    }

    private List<String> legacyMissingSkills(AnalysisEngine.Result result) {
        return requiredSkillNames(result, true);
    }

    private List<String> requiredSkillNames(AnalysisEngine.Result result, boolean unsatisfiedOnly) {
        return result.scored()
                .matchesIn(RequirementCategory.REQUIRED_SKILL).stream()
                .filter(m -> m.isUnsatisfied() == unsatisfiedOnly)
                .map(RequirementMatch::requirement)
                .distinct()
                .toList();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize analysis result", e);
        }
    }
}
