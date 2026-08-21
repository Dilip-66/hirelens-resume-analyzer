package com.resumerag.chunking;

import com.resumerag.chunking.strategy.ChunkingStrategy;
import com.resumerag.chunking.strategy.SectionAwareChunker;
import com.resumerag.config.AppProperties;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for chunking resume text using configurable chunking strategies.
 */
@Service
public class ChunkingService {

    private final AppProperties appProperties;
    private final ChunkingStrategy defaultStrategy;

    public ChunkingService(AppProperties appProperties) {
        this.appProperties = appProperties;
        this.defaultStrategy = new SectionAwareChunker();
    }

    /**
     * Chunks the raw text using the default section-aware strategy with configured chunk size and overlap.
     *
     * @param rawText the raw resume text to chunk
     * @return list of text chunks
     */
    public List<ChunkingStrategy.TextChunk> chunk(String rawText) {
        return defaultStrategy.chunk(rawText, appProperties.getRag().getChunkSize(), appProperties.getRag().getChunkOverlap());
    }

    /**
     * Chunks the raw text using the specified strategy.
     *
     * @param rawText   the raw text to chunk
     * @param strategy  the chunking strategy to use
     * @param chunkSize the maximum chunk size
     * @param overlap   the overlap between consecutive chunks
     * @return list of text chunks
     */
    public List<ChunkingStrategy.TextChunk> chunk(String rawText, ChunkingStrategy strategy, int chunkSize, int overlap) {
        return strategy.chunk(rawText, chunkSize, overlap);
    }
}