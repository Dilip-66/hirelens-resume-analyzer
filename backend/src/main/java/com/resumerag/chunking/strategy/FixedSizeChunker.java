package com.resumerag.chunking.strategy;

import java.util.ArrayList;
import java.util.List;

/**
 * Fixed-size chunking strategy that splits text into equal-sized chunks with configurable overlap.
 * Does not perform any section detection or preservation.
 */
public class FixedSizeChunker implements ChunkingStrategy {

    @Override
    public List<TextChunk> chunk(String rawText, int chunkSize, int overlap) {
        List<TextChunk> chunks = new ArrayList<>();
        if (rawText == null || rawText.isBlank()) {
            return chunks;
        }

        String text = rawText.trim();
        if (text.isEmpty()) {
            return chunks;
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be positive");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap must be non-negative");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be less than chunkSize");
        }

        int index = 0;
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String content = text.substring(start, end).trim();
            if (!content.isEmpty()) {
                chunks.add(new TextChunk(content, "", index++));
            }
            if (end == text.length()) {
                break;
            }
            start += chunkSize - overlap;
        }

        return chunks;
    }
}