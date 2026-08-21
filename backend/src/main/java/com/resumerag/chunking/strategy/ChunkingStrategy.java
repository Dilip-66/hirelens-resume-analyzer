package com.resumerag.chunking.strategy;

import java.util.List;

/**
 * Strategy interface for text chunking algorithms.
 */
public interface ChunkingStrategy {

    /**
     * Record representing a chunk of text with metadata.
     */
    record TextChunk(String content, String section, int index) {}

    /**
     * Chunk the raw text into segments.
     *
     * @param rawText  the text to chunk
     * @param chunkSize maximum chunk size
     * @param overlap  overlap between consecutive chunks
     * @return list of TextChunk objects
     */
    List<TextChunk> chunk(String rawText, int chunkSize, int overlap);
}