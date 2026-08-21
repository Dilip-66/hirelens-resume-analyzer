package com.resumerag.pipeline.ingestion;

import com.resumerag.chunking.ChunkingService;
import com.resumerag.chunking.strategy.ChunkingStrategy;
import com.resumerag.embedding.EmbeddingService;
import com.resumerag.model.Resume;
import com.resumerag.model.ResumeChunk;
import com.resumerag.parser.DocumentParserService;
import com.resumerag.repository.ChunkVectorRepository;
import com.resumerag.repository.ResumeChunkRepository;
import com.resumerag.repository.ResumeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class IngestionPipeline {

    private final DocumentParserService documentParserService;
    private final ChunkingService chunkingService;
    private final EmbeddingService embeddingService;
    private final ResumeRepository resumeRepository;
    private final ResumeChunkRepository resumeChunkRepository;
    private final ChunkVectorRepository chunkVectorRepository;

    public IngestionPipeline(DocumentParserService documentParserService,
                             ChunkingService chunkingService,
                             EmbeddingService embeddingService,
                             ResumeRepository resumeRepository,
                             ResumeChunkRepository resumeChunkRepository,
                             ChunkVectorRepository chunkVectorRepository) {
        this.documentParserService = documentParserService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.resumeRepository = resumeRepository;
        this.resumeChunkRepository = resumeChunkRepository;
        this.chunkVectorRepository = chunkVectorRepository;
    }

    public Resume ingest(UUID userId, MultipartFile file) {
        String rawText;
        try {
            rawText = documentParserService.extractText(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to extract text from file", e);
        }

        if (rawText.isBlank()) {
            throw new IllegalArgumentException("No extractable text found in the uploaded file.");
        }

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(file.getOriginalFilename());
        resume.setRawText(rawText);
        resume = resumeRepository.save(resume);

        List<ChunkingStrategy.TextChunk> textChunks = chunkingService.chunk(rawText);
        List<float[]> embeddings = embeddingService.embedBatch(
                textChunks.stream().map(ChunkingStrategy.TextChunk::content).toList());

        for (int i = 0; i < textChunks.size(); i++) {
            ChunkingStrategy.TextChunk tc = textChunks.get(i);
            ResumeChunk chunk = new ResumeChunk(resume.getId(), tc.index(), tc.section(), tc.content());
            chunk = resumeChunkRepository.save(chunk);
            chunkVectorRepository.saveEmbedding(chunk.getId(), embeddings.get(i));
        }

        return resume;
    }
}
