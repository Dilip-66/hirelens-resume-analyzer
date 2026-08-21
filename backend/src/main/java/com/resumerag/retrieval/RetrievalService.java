package com.resumerag.retrieval;

import com.resumerag.algorithm.SkillDictionary;
import com.resumerag.algorithm.SkillTrie;
import com.resumerag.dto.RetrievedChunk;
import com.resumerag.embedding.EmbeddingService;
import com.resumerag.repository.ChunkVectorRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class RetrievalService {

    private static final int CANDIDATE_POOL_SIZE = 30;

    private final ChunkVectorRepository chunkVectorRepository;
    private final EmbeddingService embeddingService;
    private final Reranker reranker;
    private final SkillTrie skillTrie;

    public RetrievalService(ChunkVectorRepository chunkVectorRepository,
                            EmbeddingService embeddingService,
                            Reranker reranker) {
        this.chunkVectorRepository = chunkVectorRepository;
        this.embeddingService = embeddingService;
        this.reranker = reranker;
        this.skillTrie = new SkillTrie();
        this.skillTrie.insertAll(SkillDictionary.SKILLS);
    }

    public List<RetrievedChunk> retrieveRelevantChunks(UUID resumeId, String jobDescriptionText) {
        float[] queryEmbedding = embeddingService.embed(jobDescriptionText);
        Set<String> requiredSkills = skillTrie.findAll(jobDescriptionText);

        List<ChunkVectorRepository.CandidateRow> candidates =
                chunkVectorRepository.findNearestCandidates(resumeId, queryEmbedding, CANDIDATE_POOL_SIZE);

        List<Reranker.ScoredCandidate> scored = reranker.rerank(candidates, queryEmbedding, requiredSkills, skillTrie);

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
        return skillTrie.findAll(jobDescriptionText);
    }

    public Set<String> extractSkillsFromText(String text) {
        return skillTrie.findAll(text);
    }
}
