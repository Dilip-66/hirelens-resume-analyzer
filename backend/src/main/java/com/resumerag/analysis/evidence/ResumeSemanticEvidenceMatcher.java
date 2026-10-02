package com.resumerag.analysis.evidence;

import com.resumerag.algorithm.CosineSimilarity;
import com.resumerag.analysis.scoring.ScoringConfiguration;
import com.resumerag.embedding.EmbeddingService;
import com.resumerag.repository.ChunkVectorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Semantic matching over the resume's own chunk embeddings.
 *
 * <p>A single resume is a few dozen chunks. Rather than issue one KNN query per
 * requirement - twenty-six round trips for a typical job description - the chunk
 * vectors are loaded once and every requirement is scored against them in
 * process. The embeddings are reused rather than recomputed, which is the only
 * thing that makes per-requirement semantic matching affordable at all.
 *
 * <p>Degrades to no results when the embedding service is unavailable. That is
 * deliberate: an analysis that loses its semantic layer falls back to lexical
 * matching and reports slightly weaker evidence, whereas one that throws because
 * a local model was not running reports nothing at all.
 */
@Component
public class ResumeSemanticEvidenceMatcher implements SemanticEvidenceMatcher {

    private static final Logger log = LoggerFactory.getLogger(ResumeSemanticEvidenceMatcher.class);

    /**
     * A resume's chunk vectors, cached across analyses.
     *
     * <p>Bounded, because this outlives a single request: without a bound it
     * becomes a memory leak that grows with every resume the service has ever
     * analysed. The entries are immutable, so a hit needs no synchronisation
     * beyond the map's own.
     */
    private static final int MAX_CACHED_RESUMES = 8;

    private final ChunkVectorRepository chunkVectorRepository;
    private final EmbeddingService embeddingService;
    private final double weakThreshold;

    private final ConcurrentMap<UUID, List<ChunkVector>> cache = new ConcurrentHashMap<>();

    public ResumeSemanticEvidenceMatcher(ChunkVectorRepository chunkVectorRepository,
                                         EmbeddingService embeddingService,
                                         ScoringConfiguration scoringConfiguration) {
        this.chunkVectorRepository = chunkVectorRepository;
        this.embeddingService = embeddingService;
        this.weakThreshold = scoringConfiguration.getSemanticWeakThreshold();
    }

    @Override
    public Index indexFor(UUID resumeId) {
        return new ResumeIndex(vectorsFor(resumeId));
    }

    private List<ChunkVector> vectorsFor(UUID resumeId) {
        if (resumeId == null) {
            return List.of();
        }
        List<ChunkVector> cached = cache.get(resumeId);
        if (cached != null) {
            return cached;
        }
        List<ChunkVector> loaded = readVectors(resumeId);
        if (cache.size() >= MAX_CACHED_RESUMES) {
            cache.clear();
        }
        cache.put(resumeId, loaded);
        return loaded;
    }

    private List<ChunkVector> readVectors(UUID resumeId) {
        try {
            return chunkVectorRepository.findWithEmbeddings(resumeId).stream()
                    .map(row -> new ChunkVector(row.content(), row.section(), row.embedding()))
                    .toList();
        } catch (Exception e) {
            log.warn("Could not read resume chunk vectors (resumeId={}); semantic matching is disabled "
                    + "for this analysis: {}", resumeId, e.getMessage());
            return List.of();
        }
    }

    private final class ResumeIndex implements Index {

        private final List<ChunkVector> chunks;

        ResumeIndex(List<ChunkVector> chunks) {
            this.chunks = chunks;
        }

        @Override
        public List<List<Hit>> rankAll(List<String> requirementTexts, int limit) {
            if (requirementTexts == null || requirementTexts.isEmpty() || chunks.isEmpty()) {
                return requirementTexts == null
                        ? List.of()
                        : requirementTexts.stream().map(text -> List.<Hit>of()).toList();
            }
            List<float[]> vectors;
            try {
                vectors = embeddingService.embedBatch(requirementTexts);
            } catch (Exception e) {
                log.warn("Semantic evidence unavailable, continuing with lexical matching only: {}",
                        e.getMessage());
                return requirementTexts.stream().map(text -> List.<Hit>of()).toList();
            }
            List<List<Hit>> ranked = new ArrayList<>(requirementTexts.size());
            for (int i = 0; i < requirementTexts.size(); i++) {
                float[] query = i < vectors.size() ? vectors.get(i) : null;
                ranked.add(rankAgainst(query, limit));
            }
            return ranked;
        }

        @Override
        public List<Hit> rank(String requirementText, int limit) {
            if (requirementText == null || requirementText.isBlank() || limit <= 0) {
                return List.of();
            }
            return rankAll(List.of(requirementText), limit).get(0);
        }

        private List<Hit> rankAgainst(float[] query, int limit) {
            if (query == null || query.length == 0) {
                return List.of();
            }
            List<Hit> hits = new ArrayList<>();
            boolean dimensionMismatch = false;
            for (ChunkVector chunk : chunks) {
                if (chunk.embedding().length != query.length) {
                    dimensionMismatch = true;
                    continue;
                }
                double similarity = CosineSimilarity.of(query, chunk.embedding());
                if (similarity < weakThreshold) {
                    continue;
                }
                hits.add(new Hit(chunk.content(), chunk.section(), similarity));
            }
            if (dimensionMismatch) {
                // The resume was embedded with a different model than the one
                // configured now. Comparing across dimensions would produce
                // numbers that look like similarities and mean nothing, so say so
                // once rather than silently returning fewer hits for an unclear
                // reason.
                log.warn("Resume chunk vectors have a different dimension to the configured embedding "
                        + "model; semantic evidence was skipped for {} chunk(s). Re-upload the resume "
                        + "or align OLLAMA_EMBEDDING_DIMENSIONS.", chunks.size());
            }
            hits.sort(Comparator.comparingDouble(Hit::similarity).reversed());
            return hits.size() <= limit ? List.copyOf(hits) : List.copyOf(hits.subList(0, limit));
        }
    }

    private record ChunkVector(String content, String section, float[] embedding) {}
}
