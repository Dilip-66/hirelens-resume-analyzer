package com.resumerag.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.resumerag.config.AppProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
public class OpenAiEmbeddingProvider implements EmbeddingProvider {

    private final WebClient openAiWebClient;
    private final AppProperties appProperties;

    public OpenAiEmbeddingProvider(WebClient openAiWebClient, AppProperties appProperties) {
        this.openAiWebClient = openAiWebClient;
        this.appProperties = appProperties;
    }

    @Override
    public float[] embed(String text) {
        return embedBatch(List.of(text)).get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        Map<String, Object> body = Map.of(
                "model", appProperties.getOpenai().getEmbeddingModel(),
                "input", texts
        );

        JsonNode response = openAiWebClient.post()
                .uri("/embeddings")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(JsonNode.class)
                .block();

        if (response == null || !response.has("data")) {
            throw new RuntimeException("OpenAI embeddings call returned no data");
        }

        return java.util.stream.StreamSupport.stream(response.get("data").spliterator(), false)
                .map(node -> {
                    JsonNode arr = node.get("embedding");
                    float[] vec = new float[arr.size()];
                    for (int i = 0; i < arr.size(); i++) vec[i] = (float) arr.get(i).asDouble();
                    return vec;
                })
                .toList();
    }
}
