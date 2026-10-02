package com.resumerag.pipeline.ingestion;

import com.resumerag.chunking.ChunkingService;
import com.resumerag.chunking.strategy.ChunkingStrategy;
import com.resumerag.embedding.EmbeddingService;
import com.resumerag.exception.DocumentParsingException;
import com.resumerag.exception.EmbeddingException;
import com.resumerag.model.Resume;
import com.resumerag.model.ResumeChunk;
import com.resumerag.parser.DocumentParserService;
import com.resumerag.repository.ChunkVectorRepository;
import com.resumerag.parser.CandidateNameExtractor;
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
    private final CandidateNameExtractor candidateNameExtractor;

    public IngestionPipeline(DocumentParserService documentParserService,
                             ChunkingService chunkingService,
                             EmbeddingService embeddingService,
                             ResumeRepository resumeRepository,
                             ResumeChunkRepository resumeChunkRepository,
                             ChunkVectorRepository chunkVectorRepository,
                             CandidateNameExtractor candidateNameExtractor) {
        this.documentParserService = documentParserService;
        this.chunkingService = chunkingService;
        this.embeddingService = embeddingService;
        this.resumeRepository = resumeRepository;
        this.resumeChunkRepository = resumeChunkRepository;
        this.chunkVectorRepository = chunkVectorRepository;
        this.candidateNameExtractor = candidateNameExtractor;
    }

    public Resume ingest(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded.");
        }

        String rawText;
        try {
            rawText = documentParserService.extractText(file);
        } catch (IOException e) {
            throw new DocumentParsingException("Failed to read the uploaded file.", e);
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new DocumentParsingException(
                    "Could not extract text from the file. Scanned/image-only PDFs are not supported.", e);
        }

        if (rawText.isBlank()) {
            throw new DocumentParsingException(
                    "No extractable text found in the uploaded file. If this is a scanned or image-only PDF, "
                            + "export a text-based PDF or upload a .docx instead.",
                    new IllegalStateException("Extracted text was blank"));
        }

        Resume resume = new Resume();
        resume.setUserId(userId);
        resume.setFileName(file.getOriginalFilename());
        resume.setRawText(rawText);
        resume.setCandidateName(candidateNameExtractor.extract(rawText, file.getOriginalFilename()));
        resume = resumeRepository.save(resume);

        List<ChunkingStrategy.TextChunk> textChunks = chunkingService.chunk(rawText);
        if (textChunks.isEmpty()) {
            throw new DocumentParsingException(
                    "The resume produced no indexable sections.", new IllegalStateException("No chunks produced"));
        }

        List<float[]> embeddings = embeddingService.embedBatch(
                textChunks.stream().map(ChunkingStrategy.TextChunk::content).toList());

        if (embeddings.size() != textChunks.size()) {
            throw new EmbeddingException("Expected " + textChunks.size() + " embeddings but received "
                    + embeddings.size() + ".", new IllegalStateException("Embedding count mismatch"));
        }

        for (int i = 0; i < textChunks.size(); i++) {
            ChunkingStrategy.TextChunk tc = textChunks.get(i);
            ResumeChunk chunk = new ResumeChunk(resume.getId(), tc.index(), tc.section(), tc.content());
            // saveAndFlush, not save: a plain save only stages the entity in the
            // persistence context, and Hibernate defers the INSERT until the next
            // flush. The raw JdbcTemplate UPDATE in saveEmbedding would then run
            // before the row exists, match zero rows, and leave the chunk persisted
            // with a NULL embedding - retrieval silently returns no evidence.
            chunk = resumeChunkRepository.saveAndFlush(chunk);
            chunkVectorRepository.saveEmbedding(chunk.getId(), embeddings.get(i));
        }

        return resume;
    }
}
