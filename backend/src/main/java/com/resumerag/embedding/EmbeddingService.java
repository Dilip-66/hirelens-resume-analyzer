package com.resumerag.embedding;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingProvider embeddingProvider;

    public EmbeddingService(EmbeddingProvider embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public float[] embed(String text) {
        return embeddingProvider.embed(text);
    }

    public List<float[]> embedBatch(List<String> texts) {
        return embeddingProvider.embedBatch(texts);
    }
}
