package com.resumerag.generation;

import com.resumerag.analysis.model.AnalysisNarrative;
import com.resumerag.config.AppProperties;
import com.resumerag.dto.AnalysisResult;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.exception.AnalysisFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private static final int CHARS_PER_TOKEN = 4;

    private static final String SYSTEM_PROMPT = """
        You are an expert technical recruiter and resume coach. You will be given:
        1. A job description.
        2. The most relevant excerpts retrieved from a candidate's resume (not the whole resume).
        3. A deterministically computed list of required skills found in the job description,
           and which of those skills were detected in the retrieved excerpts.

        Base your analysis ONLY on the retrieved excerpts and the provided skill lists - do not
        assume information that isn't present. Respond with strict JSON matching the schema
        given in the user message and nothing else: no markdown fences, no commentary.
        """;

    private final OllamaChatClient ollamaChatClient;
    private final AppProperties appProperties;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public GenerationService(OllamaChatClient ollamaChatClient,
                             AppProperties appProperties,
                             PromptBuilder promptBuilder,
                             ResponseParser responseParser) {
        this.ollamaChatClient = ollamaChatClient;
        this.appProperties = appProperties;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    /**
     * Writes the prose for an analysis whose numbers are already settled.
     *
     * <p>Strictly narrative. The model is given the job description, the retrieved
     * excerpts and the already-computed match results, and returns a summary,
     * strengths and gaps - no score. That separation is deliberate: the previous
     * {@link #analyze} asked the model for {@code matchScore} and stored whatever
     * came back, so the headline number on every report was a 7B model's opinion
     * of how well the candidate was doing, free to contradict the skill scan
     * printed a few lines above it in the same prompt. Confining it to prose means
     * a slow, cold or missing LLM can no longer change or block a verdict.
     */
    public AnalysisNarrative narrate(String jobDescriptionText,
                                     List<RetrievedChunk> retrievedChunks,
                                     List<PromptBuilder.RequirementSummary> requirements) {
        String userPrompt = promptBuilder.buildNarrativePrompt(
                jobDescriptionText, retrievedChunks, requirements, promptCharBudget());

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", NARRATIVE_SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt));

        String content = ollamaChatClient.complete(messages, 0.3, 0);
        return responseParser.parseNarrative(content);
    }

    private static final String NARRATIVE_SYSTEM_PROMPT = """
        You are an expert technical recruiter and resume coach. You will be given:
        1. A job description.
        2. The most relevant excerpts retrieved from a candidate's resume.
        3. Match results already computed by a deterministic engine.

        Write only the narrative. The score has already been calculated and is not
        your responsibility. Base what you write ONLY on the provided match results and
        excerpts - never assume information that is not present. Respond with strict JSON
        matching the schema given in the user message and nothing else: no markdown
        fences, no commentary, no numbers.
        """;

    public AnalysisResult analyze(String jobDescriptionText,
                                  List<RetrievedChunk> retrievedChunks,
                                  Set<String> requiredSkills,
                                  Set<String> matchedSkills) {
        Set<String> missingSkills = new LinkedHashSet<>(requiredSkills);
        missingSkills.removeAll(matchedSkills);

        String userPrompt = promptBuilder.buildUserPrompt(
                jobDescriptionText, retrievedChunks, requiredSkills, matchedSkills, missingSkills,
                promptCharBudget());

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", userPrompt));

        String content = ollamaChatClient.complete(messages, 0.3, 0);
        return responseParser.parse(content, matchedSkills, missingSkills);
    }

    /**
     * How many characters of prompt we are willing to spend, derived from the
     * model's context window.
     *
     * <p>Without this, raising {@code top-k} or lengthening a job description
     * silently overruns {@code num_ctx}. Ollama then evicts from the middle of
     * the prompt, so the model answers confidently from evidence that is no
     * longer in front of it and nothing anywhere reports an error - which is
     * exactly how analyses end up citing gaps that the resume never had.
     *
     * <p>Leaves 30% of the window for the system prompt and the model's own
     * completion. 4 chars/token is the usual English approximation and errs
     * toward a smaller prompt.
     */
    private int promptCharBudget() {
        int numCtx = appProperties.getLlm().getNumCtx();
        if (numCtx <= 0) {
            return 0;
        }
        int promptTokens = (int) (numCtx * 0.70);
        int chars = promptTokens * CHARS_PER_TOKEN;
        log.debug("Prompt budget: num_ctx={} -> {} chars", numCtx, chars);
        return chars;
    }

}
