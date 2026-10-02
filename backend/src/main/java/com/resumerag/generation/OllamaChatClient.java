package com.resumerag.generation;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumerag.config.AppProperties;
import com.resumerag.exception.AnalysisFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * The single point of contact with the local Ollama daemon.
 *
 * <p>Extracted from {@link GenerationService} so the resume analysis and the AI
 * assistant share one HTTP client, one model configuration and one set of
 * failure messages. Before this existed, a second chat feature would have had to
 * duplicate the base URL, the model name, the num_ctx handling and the
 * "is Ollama even running?" error copy - and would inevitably drift from the
 * analysis pipeline's behaviour.
 */
@Component
public class OllamaChatClient {

    private static final Logger log = LoggerFactory.getLogger(OllamaChatClient.class);

    private final WebClient ollamaWebClient;
    private final AppProperties appProperties;

    public OllamaChatClient(WebClient ollamaWebClient, AppProperties appProperties) {
        this.ollamaWebClient = ollamaWebClient;
        this.appProperties = appProperties;
    }

    /**
     * Runs a chat completion and returns the assistant message content.
     *
     * @param messages     alternating system/user turns, oldest first
     * @param temperature  sampling temperature
     * @param timeoutSeconds overrides the default LLM timeout when > 0
     */
    public String complete(List<Map<String, String>> messages, double temperature, int timeoutSeconds) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("model", appProperties.getLlm().getChatModel());
        body.put("temperature", temperature);
        body.put("stream", false);
        body.put("messages", messages);

        if (appProperties.getLlm().isJsonMode()) {
            // Small local models emit valid JSON far more reliably with this set.
            body.put("response_format", Map.of("type", "json_object"));
        }

        int numCtx = appProperties.getLlm().getNumCtx();
        if (numCtx > 0) {
            body.put("options", Map.of("num_ctx", numCtx));
        }

        Duration timeout = Duration.ofSeconds(
                timeoutSeconds > 0 ? timeoutSeconds
                        : Math.max(1, appProperties.getLlm().getChatTimeoutSeconds()));

        JsonNode response;
        try {
            response = ollamaWebClient.post()
                    .uri("/chat/completions")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(timeout);
        } catch (WebClientResponseException e) {
            log.error("Ollama chat completion failed with status {}", e.getStatusCode(), e);
            throw new AnalysisFailedException(
                    "The local model rejected the request (HTTP " + e.getStatusCode().value()
                            + "). Check that the model is pulled: ollama pull " + appProperties.getLlm().getChatModel());
        } catch (RuntimeException e) {
            log.error("Ollama chat completion call failed", e);
            throw new AnalysisFailedException(
                    "Could not reach the local Ollama daemon at " + appProperties.getLlm().getBaseUrl()
                            + ": " + e.getMessage() + ". Is `ollama serve` running?");
        }

        return extractContent(response);
    }

    private String extractContent(JsonNode response) {
        if (response == null || !response.has("choices") || response.get("choices").isEmpty()) {
            throw new AnalysisFailedException("The local model returned no choices.");
        }
        JsonNode message = response.get("choices").get(0).get("message");
        if (message == null || message.get("content") == null || message.get("content").isNull()) {
            throw new AnalysisFailedException("The local model returned an empty message.");
        }
        String content = message.get("content").asText();
        if (content == null || content.isBlank()) {
            throw new AnalysisFailedException("The local model returned an empty message.");
        }
        return content;
    }
}
