package com.resumerag.retrieval;

import com.resumerag.algorithm.TopKHeap;
import com.resumerag.config.AppProperties;
import com.resumerag.repository.ChunkVectorRepository;
import com.resumerag.algorithm.CosineSimilarity;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class Reranker {

    private final AppProperties appProperties;
    private final CandidateScorer candidateScorer;

    public Reranker(AppProperties appProperties, CandidateScorer candidateScorer) {
        this.appProperties = appProperties;
        this.candidateScorer = candidateScorer;
    }

    public List<ScoredCandidate> rerank(List<ChunkVectorRepository.CandidateRow> candidates,
                                         float[] queryEmbedding,
                                         Set<String> requiredSkills,
                                         com.resumerag.algorithm.SkillTrie skillTrie) {
        int topK = appProperties.getRag().getTopK();
        TopKHeap<ChunkVectorRepository.CandidateRow> heap = new TopKHeap<>(topK);

        for (ChunkVectorRepository.CandidateRow candidate : candidates) {
            double similarity = 1.0 - candidate.distance();
            Set<String> chunkSkills = skillTrie.findAll(candidate.content());
            chunkSkills.retainAll(requiredSkills);
            double compositeScore = similarity + (chunkSkills.size() * 0.03);
            heap.offer(candidate, compositeScore);
        }

        List<ScoredCandidate> results = new ArrayList<>();
        while (!heap.isEmpty()) {
            TopKHeap.Scored<ChunkVectorRepository.CandidateRow> scored = heap.poll();
            ChunkVectorRepository.CandidateRow c = scored.item;
            double similarity = 1.0 - c.distance();
            Set<String> chunkSkills = skillTrie.findAll(c.content());
            chunkSkills.retainAll(requiredSkills);
            results.add(new ScoredCandidate(c, similarity, scored.score, chunkSkills));
        }
        return results;
    }

    public record ScoredCandidate(ChunkVectorRepository.CandidateRow candidate,
                                   double similarity,
                                   double score,
                                   Set<String> matchedSkills) {}
}
