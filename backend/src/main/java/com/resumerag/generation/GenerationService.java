package com.resumerag.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumerag.config.AppProperties;
import com.resumerag.dto.AnalysisResult;
import com.resumerag.dto.RetrievedChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class GenerationService {

    private static final Logger log = LoggerFactory.getLogger(GenerationService.class);

    private static final String SYSTEM_PROMPT = """
        You are an expert technical recruiter and resume coach. You will be given:
        1. A job description.
        2. The most relevant excerpts retrieved from a candidate's resume (not the whole resume).
        3. A deterministically computed list of required skills found in the job description,
           and which of those skills were detected in the retrieved excerpts.

        Base your analysis ONLY on the retrieved excerpts and the provided skill lists - do not
        assume information that isn't present. Respond with strict JSON matching this schema and
        nothing else, no markdown fences:
        {
          "matchScore": <integer 0-100>,
          "summary": <string, 2-3 sentences>,
          "strengths": [<string>, ...],
          "gaps": [<string>, ...],
          "matchedSkills": [<string>, ...],
          "missingSkills": [<string>, ...]
        }
        """;

    private final WebClient openAiWebClient;
    private final AppProperties appProperties;
    private final PromptBuilder promptBuilder;
    private final ResponseParser responseParser;

    public GenerationService(WebClient openAiWebClient,
                              AppProperties appProperties,
                              PromptBuilder promptBuilder,
                              ResponseParser responseParser) {
        this.openAiWebClient = openAiWebClient;
        this.appProperties = appProperties;
        this.promptBuilder = promptBuilder;
        this.responseParser = responseParser;
    }

    public AnalysisResult analyze(String jobDescriptionText,
                                   List<RetrievedChunk> retrievedChunks,
                                   Set<String> requiredSkills,
                                   Set<String> matchedSkills) {
        Set<String> missingSkills = new LinkedHashSet<>(requiredSkills);
        missingSkills.removeAll(matchedSkills);

        String userPrompt = promptBuilder.buildUserPrompt(
                jobDescriptionText, retrievedChunks, requiredSkills, matchedSkills, missingSkills);

        Map<String, Object> body = Map.of(
                "model", appProperties.getOpenai().getChatModel(),
                "temperature", 0.3,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", SYSTEM_PROMPT),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        JsonNode response = openAiWebClient.post()
                .uri("/chat/completions")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        String content = response.get("choices").get(0).get("message").get("content").asText();
        return responseParser.parse(content);
    }
}
