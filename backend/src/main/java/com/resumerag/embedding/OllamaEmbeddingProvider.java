package com.resumerag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumerag.config.AppProperties;
import com.resumerag.exception.EmbeddingException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Component
public class OllamaEmbeddingProvider implements EmbeddingProvider {

    /** Ollama has no documented per-request batch cap; keep batches small so one slow embed can't stall a whole resume. */
    private static final int MAX_BATCH_SIZE = 16;

    private final WebClient ollamaWebClient;
    private final AppProperties appProperties;

    public OllamaEmbeddingProvider(WebClient ollamaWebClient, AppProperties appProperties) {
        this.ollamaWebClient = ollamaWebClient;
        this.appProperties = appProperties;
    }

    private Duration requestTimeout() {
        return Duration.ofSeconds(Math.max(1, appProperties.getLlm().getEmbeddingTimeoutSeconds()));
    }

    @Override
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new EmbeddingException("Cannot embed empty text.");
        }
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<float[]> all = new ArrayList<>(texts.size());
        for (int start = 0; start < texts.size(); start += MAX_BATCH_SIZE) {
            List<String> batch = texts.subList(start, Math.min(start + MAX_BATCH_SIZE, texts.size()));
            all.addAll(requestBatch(batch));
        }
        return all;
    }

    private List<float[]> requestBatch(List<String> inputs) {
        Map<String, Object> body = Map.of(
                "model", appProperties.getLlm().getEmbeddingModel(),
                "input", inputs
        );

        JsonNode response;
        try {
            response = ollamaWebClient.post()
                    .uri("/embeddings")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(JsonNode.class)
                    .block(requestTimeout());
        } catch (WebClientResponseException e) {
            throw new EmbeddingException(
                    "Embedding request rejected (HTTP " + e.getStatusCode().value() + "): " + e.getResponseBodyAsString()
                            + " - is the model pulled? Try: ollama pull " + appProperties.getLlm().getEmbeddingModel());
        } catch (RuntimeException e) {
            throw new EmbeddingException(
                    "Could not reach the local Ollama daemon at " + appProperties.getLlm().getBaseUrl()
                            + ": " + e.getMessage() + ". Is `ollama serve` running?");
        }

        if (response == null || !response.has("data") || !response.get("data").isArray()) {
            throw new EmbeddingException("Embedding request returned no data.");
        }

        int expectedDimensions = appProperties.getLlm().getEmbeddingDimensions();

        // The API is documented to return one entry per input, but it carries an
        // explicit "index" per entry. Reading the array positionally and ignoring
        // that index silently pairs chunk N with the wrong vector.
        List<float[]> vectors = new ArrayList<>(response.get("data").size());
        for (JsonNode node : response.get("data")) {
            JsonNode arr = node.get("embedding");
            if (arr == null || !arr.isArray() || arr.isEmpty()) {
                throw new EmbeddingException("Embedding response contained an empty vector.");
            }
            if (expectedDimensions > 0 && arr.size() != expectedDimensions) {
                throw new EmbeddingException(
                        "Embedding dimension mismatch: expected " + expectedDimensions
                                + " but received " + arr.size()
                                + ". The pgvector column is vector(" + expectedDimensions + ") and"
                                + " OLLAMA_EMBEDDING_DIMENSIONS must match the model.");
            }
            float[] vec = new float[arr.size()];
            for (int i = 0; i < arr.size(); i++) {
                vec[i] = (float) arr.get(i).asDouble();
            }
            vectors.add(vec);
        }

        if (vectors.size() != inputs.size()) {
            throw new EmbeddingException(
                    "Embedding count mismatch: sent " + inputs.size() + " inputs but got back " + vectors.size() + ".");
        }

        List<Indexed> indexed = new ArrayList<>(vectors.size());
        for (int i = 0; i < vectors.size(); i++) {
            JsonNode indexNode = response.get("data").get(i).get("index");
            indexed.add(new Indexed(indexNode == null ? i : indexNode.asInt(), vectors.get(i)));
        }
        indexed.sort(Comparator.comparingInt(Indexed::index));

        List<float[]> ordered = new ArrayList<>(indexed.size());
        for (int i = 0; i < indexed.size(); i++) {
            if (indexed.get(i).index() != i) {
                throw new EmbeddingException("Embedding response indices were not a 0..n-1 sequence.");
            }
            ordered.add(indexed.get(i).vector());
        }
        return ordered;
    }

    private record Indexed(int index, float[] vector) {}
}
