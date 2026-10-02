package com.resumerag.assistant;

import com.resumerag.config.AppProperties;
import com.resumerag.dto.AiChatRequest;
import com.resumerag.dto.AiChatResponse;
import com.resumerag.dto.AiSource;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.exception.AnalysisFailedException;
import com.resumerag.exception.ResourceNotFoundException;
import com.resumerag.generation.OllamaChatClient;
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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class AiAssistantService {

    private static final Logger log = LoggerFactory.getLogger(AiAssistantService.class);

    private final AnalysisRepository analysisRepository;
    private final ResumeRepository resumeRepository;
    private final JobDescriptionRepository jobDescriptionRepository;
    private final RetrievalService retrievalService;
    private final JobPassageSelector jobPassageSelector;
    private final AiAssistantPromptBuilder promptBuilder;
    private final AiAssistantResponseParser responseParser;
    private final OllamaChatClient ollamaChatClient;
    private final AppProperties appProperties;
    private final QuestionCatalog catalog;

    public AiAssistantService(AnalysisRepository analysisRepository,
                              ResumeRepository resumeRepository,
                              JobDescriptionRepository jobDescriptionRepository,
                              RetrievalService retrievalService,
                              JobPassageSelector jobPassageSelector,
                              AiAssistantPromptBuilder promptBuilder,
                              AiAssistantResponseParser responseParser,
                              OllamaChatClient ollamaChatClient,
                              AppProperties appProperties,
                              QuestionCatalog catalog) {
        this.analysisRepository = analysisRepository;
        this.resumeRepository = resumeRepository;
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.retrievalService = retrievalService;
        this.jobPassageSelector = jobPassageSelector;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
        this.ollamaChatClient = ollamaChatClient;
        this.appProperties = appProperties;
        this.catalog = catalog;
    }

    public AiChatResponse ask(UUID userId, AiChatRequest request) {
        String question = request.question().trim();
        if (question.isEmpty()) {
            throw new IllegalArgumentException("Please enter a question.");
        }
        if (question.length() > appProperties.getAi().getMaxQuestionChars()) {
            throw new IllegalArgumentException(
                    "That question is too long. Keep it under "
                            + appProperties.getAi().getMaxQuestionChars() + " characters.");
        }

        Analysis analysis = null;
        Resume resume = null;
        JobDescription job = null;

        if (request.analysisId() != null) {
            // Ownership is enforced here, on the server, and reported as
            // "not found" rather than "forbidden" so this endpoint cannot be used
            // to probe which analysis ids exist.
            analysis = analysisRepository.findById(request.analysisId())
                    .filter(a -> a.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Analysis not found"));

            resume = resumeRepository.findById(analysis.getResumeId())
                    .filter(r -> r.getUserId().equals(userId))
                    .orElseThrow(() -> new ResourceNotFoundException("Resume not found"));

            // job_description_id is NOT NULL in the schema, so this only guards a
            // row written before the column was required: the assistant degrades
            // to resume-only grounding rather than failing the turn.
            if (analysis.getJobDescriptionId() != null) {
                job = jobDescriptionRepository.findById(analysis.getJobDescriptionId())
                        .filter(j -> j.getUserId().equals(userId))
                        .orElseThrow(() -> new ResourceNotFoundException("Job description not found"));
            }
        }

        List<RetrievedChunk> resumeChunks = retrieveResumeChunks(resume, question);
        List<String> jobPassages = job == null
                ? List.of()
                : jobPassageSelector.select(job.getRawText(), question, appProperties.getAi().getJobPassageLimit());

        List<Map<String, String>> history = boundedHistory(request.history());

        String userPrompt = promptBuilder.build(question, resumeChunks, jobPassages,
                analysis, resume, job, history);

        Set<String> allowedSourceKeys = allowedSourceKeys(resumeChunks, job != null, analysis != null);

        String content;
        try {
            content = ollamaChatClient.complete(
                    List.of(
                            Map.of("role", "system", "content", promptBuilder.buildSystemPrompt()),
                            Map.of("role", "user", "content", userPrompt)),
                    appProperties.getAi().getTemperature(),
                    appProperties.getAi().getTimeoutSeconds());
        } catch (AnalysisFailedException e) {
            // Re-thrown with a user-facing message; the cause is already logged
            // by OllamaChatClient and must not reach the client.
            throw new AnalysisFailedException(userFacingModelError(e));
        }

        AiAssistantResponseParser.Parsed parsed = responseParser.parse(content, allowedSourceKeys);

        return new AiChatResponse(
                analysis == null ? null : analysis.getId(),
                parsed.answer(),
                parsed.suggestedFollowUps(),
                parsed.sources(),
                analysis != null);
    }

    /**
     * Resume passages come from the existing pgvector retrieval, queried with
     * the user's own question. Retrieval failures degrade to "no passages"
     * rather than failing the turn: the assistant can still answer from the
     * analysis block, and a hard failure would make a question about the score
     * unanswerable because one chunk lookup had a bad day.
     */
    private List<RetrievedChunk> retrieveResumeChunks(Resume resume, String question) {
        if (resume == null) {
            return List.of();
        }
        try {
            List<RetrievedChunk> retrieved = retrievalService.retrieveRelevantChunks(
                    resume.getId(), question, catalogSkills(resume, question));
            return retrieved.size() > appProperties.getAi().getRetrievalTopK()
                    ? retrieved.subList(0, appProperties.getAi().getRetrievalTopK())
                    : retrieved;
        } catch (RuntimeException e) {
            log.warn("Resume retrieval failed for the assistant; continuing without passages", e);
            return List.of();
        }
    }

    /**
     * Skills the question is plausibly about, so the skill-boost in
     * CandidateScorer pulls the matching resume section to the top.
     */
    private Set<String> catalogSkills(Resume resume, String question) {
        Set<String> required = new LinkedHashSet<>(retrievalService.extractRequiredSkills(question));
        // A question like "which skills am I missing" names no skills, so fall
        // back to the skills the job asks for, which is what the user means.
        if (required.isEmpty() && resume != null) {
            required.addAll(retrievalService.extractSkillsFromText(resume.getRawText()));
        }
        return required;
    }

    /**
     * Caps conversation history so a long chat cannot outgrow the context
     * window. Keeps the most recent turns, and only well-formed ones.
     */
    private List<Map<String, String>> boundedHistory(List<AiChatRequest.ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int max = appProperties.getAi().getMaxHistoryTurns();
        List<AiChatRequest.ChatTurn> valid = new ArrayList<>();
        for (AiChatRequest.ChatTurn turn : history) {
            if (turn != null && turn.content() != null && !turn.content().isBlank()) {
                // Never echo a client-supplied "system" turn back to the model.
                if (turn.role() == null || turn.isUser() || "assistant".equalsIgnoreCase(turn.role())) {
                    valid.add(turn);
                }
            }
        }
        int from = Math.max(0, valid.size() - max);
        List<Map<String, String>> bounded = new ArrayList<>();
        for (AiChatRequest.ChatTurn turn : valid.subList(from, valid.size())) {
            String role = turn.isUser() ? "user" : "assistant";
            // A client cannot smuggle in a system turn to replace our guardrails.
            String content = turn.content().length() > 600 ? turn.content().substring(0, 600) : turn.content();
            bounded.add(Map.of("role", role, "content", content));
        }
        return bounded;
    }

    /**
     * The set of citations the model is allowed to make.
     *
     * <p>Resume sections are matched strictly against the sections we actually
     * retrieved, so a citation of a part of the resume we never sent is
     * dropped. The other two are granted wholesale, and truthfully so: the
     * analysis is passed as one complete, deterministic block and the
     * job-description passages as selected spans of the posting, neither of
     * which has named sections we could have withheld. Fabricating a specific
     * "Requirements" heading we never supplied would be the failure mode here,
     * so a job-description citation is normalised to just "Requirements" or
     * "Role" by the parser rather than trusted verbatim.
     */
    private Set<String> allowedSourceKeys(List<RetrievedChunk> resumeChunks, boolean hasJob, boolean hasAnalysis) {
        Set<String> keys = new LinkedHashSet<>();
        for (RetrievedChunk chunk : resumeChunks) {
            if (chunk.section() != null) {
                keys.add(AiSource.RESUME + "::" + chunk.section().toLowerCase(Locale.ROOT));
            }
        }
        if (hasJob) {
            keys.add(AiSource.JOB_DESCRIPTION + "::*");
        }
        if (hasAnalysis) {
            keys.add(AiSource.ANALYSIS + "::*");
        }
        return keys;
    }

    private String userFacingModelError(AnalysisFailedException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        if (message.contains("Could not reach the local Ollama daemon")) {
            return "AI Assistance is temporarily unavailable. Please make sure the local AI service "
                    + "is running and try again.";
        }
        if (message.contains("HTTP 404") || message.contains("model is pulled")) {
            return "AI Assistance is temporarily unavailable: the configured local model is not installed. "
                    + "Run 'ollama pull' for the configured model and try again.";
        }
        if (message.contains("timeout") || message.contains("Timeout")) {
            return "The AI took longer than expected. Please try again.";
        }
        return "AI Assistance is temporarily unavailable. Please try again.";
    }
}
