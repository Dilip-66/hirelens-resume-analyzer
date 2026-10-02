package com.resumerag.retrieval;

import com.resumerag.algorithm.SkillTrie;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.embedding.EmbeddingService;
import com.resumerag.exception.AnalysisFailedException;
import com.resumerag.repository.ChunkVectorRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RetrievalService {

    /**
     * How many nearest chunks Postgres returns before reranking. Must stay above
     * {@code app.rag.top-k} or the reranker is handed fewer candidates than it
     * could rank, which reintroduces the silent-coverage-loss this pipeline is
     * trying to avoid. Kept modest because the search is exact KNN, not ANN.
     */
    private static final int CANDIDATE_POOL_SIZE = 60;


    private final ChunkVectorRepository chunkVectorRepository;
    private final EmbeddingService embeddingService;
    private final Reranker reranker;
    private final SkillTrie skillTrie;

    public RetrievalService(ChunkVectorRepository chunkVectorRepository,
                            EmbeddingService embeddingService,
                            Reranker reranker,
                            SkillTrie skillTrie) {
        this.chunkVectorRepository = chunkVectorRepository;
        this.embeddingService = embeddingService;
        this.reranker = reranker;
        this.skillTrie = skillTrie;
    }

    public List<RetrievedChunk> retrieveRelevantChunks(UUID resumeId, String jobDescriptionText) {
        return retrieveRelevantChunks(resumeId, jobDescriptionText, extractRequiredSkills(jobDescriptionText));
    }

    public List<RetrievedChunk> retrieveRelevantChunks(UUID resumeId,
                                                        String jobDescriptionText,
                                                        Set<String> requiredSkills) {
        if (jobDescriptionText == null || jobDescriptionText.isBlank()) {
            throw new IllegalArgumentException("Job description text is empty - cannot retrieve relevant chunks.");
        }

        float[] queryEmbedding = embeddingService.embed(jobDescriptionText);

        List<ChunkVectorRepository.CandidateRow> candidates =
                chunkVectorRepository.findNearestCandidates(resumeId, queryEmbedding, CANDIDATE_POOL_SIZE);

        if (candidates.isEmpty()) {
            throw new AnalysisFailedException(
                    "No embedded resume chunks found for this resume. Please re-upload the resume.");
        }

        List<Reranker.ScoredCandidate> scored = reranker.rerank(candidates, requiredSkills, skillTrie);

        return scored.stream()
                .map(sc -> new RetrievedChunk(
                        sc.candidate().content(),
                        sc.candidate().section(),
                        sc.similarity(),
                        sc.score(),
                        sc.matchedSkills()))
                .toList();
    }

    public Set<String> extractRequiredSkills(String jobDescriptionText) {
        if (jobDescriptionText == null || jobDescriptionText.isBlank()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(skillTrie.findAll(jobDescriptionText));
    }

    public Set<String> extractSkillsFromText(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return new LinkedHashSet<>(skillTrie.findAll(text));
    }
}
