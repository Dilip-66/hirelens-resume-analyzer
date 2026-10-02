package com.resumerag.retrieval;

import com.resumerag.algorithm.SkillTrie;
import com.resumerag.algorithm.TopKHeap;
import com.resumerag.config.AppProperties;
import com.resumerag.repository.ChunkVectorRepository;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.LinkedHashSet;
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

    /**
     * Re-ranks the ANN candidate pool down to the true top-K.
     *
     * <p>TopKHeap is a bounded min-heap: it keeps the K best items seen so far by
     * evicting the current minimum whenever a better score arrives, so the whole
     * pass costs O(N log K) instead of sorting all N candidates (O(N log N)).
     * Because the heap is a min-heap, {@code poll()} yields the <em>weakest</em>
     * entries first - the results must therefore be drained in descending order
     * before they are handed to the prompt builder, otherwise the least relevant
     * excerpt ends up first in the prompt.
     */
    public List<ScoredCandidate> rerank(List<ChunkVectorRepository.CandidateRow> candidates,
                                         Set<String> requiredSkills,
                                         SkillTrie skillTrie) {
        int topK = Math.max(1, appProperties.getRag().getTopK());
        Set<String> neededSkills = requiredSkills == null ? Collections.emptySet() : requiredSkills;

        TopKHeap<ChunkVectorRepository.CandidateRow> heap = new TopKHeap<>(topK);
        for (ChunkVectorRepository.CandidateRow candidate : candidates) {
            double similarity = 1.0 - candidate.distance();
            Set<String> chunkSkills = new LinkedHashSet<>(skillTrie.findAll(candidate.content()));
            chunkSkills.retainAll(neededSkills);
            heap.offer(candidate, candidateScorer.score(similarity, chunkSkills, neededSkills));
        }

        return heap.drainSortedDescending().stream()
                .map(scored -> {
                    ChunkVectorRepository.CandidateRow c = scored.item;
                    double similarity = 1.0 - c.distance();
                    Set<String> chunkSkills = new LinkedHashSet<>(skillTrie.findAll(c.content()));
                    chunkSkills.retainAll(neededSkills);
                    return new ScoredCandidate(c, similarity, scored.score, chunkSkills);
                })
                .toList();
    }

    public record ScoredCandidate(ChunkVectorRepository.CandidateRow candidate,
                                   double similarity,
                                   double score,
                                   Set<String> matchedSkills) {}
}
