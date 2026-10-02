package com.resumerag.analysis.evidence;

import com.resumerag.embedding.EmbeddingProvider;
import com.resumerag.repository.ChunkVectorRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The semantic stage, driven by a deterministic fake embedder.
 *
 * <p>Uses a hand-written vector scheme rather than a real model so the behaviour
 * under test is the thresholding, not the embeddings. The two properties that
 * matter are both about restraint: a passage below the relevance bar must produce
 * no evidence at all, and an embedding failure must cost accuracy rather than the
 * whole analysis. Both are what keep the engine from asking a model to invent
 * evidence for a requirement the resume says nothing about.
 */
class ResumeSemanticEvidenceMatcherTest {

    private static final UUID RESUME_ID = UUID.randomUUID();

    /** Vectors that make "docker" similar to container work and nothing like a review. */
    private static final class FakeEmbedder implements EmbeddingProvider {
        @Override
        public float[] embed(String text) {
            return embedBatch(List.of(text)).get(0);
        }

        @Override
        public List<float[]> embedBatch(List<String> texts) {
            return texts.stream().map(FakeEmbedder::vectorFor).toList();
        }

        private static float[] vectorFor(String text) {
            String lower = text.toLowerCase();
            float docker = (lower.contains("docker") || lower.contains("container")) ? 1f : 0f;
            float review = (lower.contains("review") || lower.contains("mentor")) ? 1f : 0f;
            return new float[]{docker, review, 0.1f};
        }
    }

    private ResumeSemanticEvidenceMatcher matcher(List<ChunkVectorRepository.ChunkWithVector> chunks) {
        return new ResumeSemanticEvidenceMatcher(
                new ChunkVectorRepository(null) {
                    @Override
                    public List<ChunkWithVector> findWithEmbeddings(UUID resumeId) {
                        return chunks;
                    }
                },
                new com.resumerag.embedding.EmbeddingService(new FakeEmbedder()),
                new com.resumerag.analysis.scoring.ScoringConfiguration());
    }

    @Test
    @DisplayName("A semantically similar passage is returned as a hit")
    void aSimilarPassageIsReturned() {
        SemanticEvidenceMatcher.Index index = matcher(List.of(
                new ChunkVectorRepository.ChunkWithVector(
                        "Containerized the build and shipped it.", "experience", new float[]{1f, 0f, 0.1f}),
                new ChunkVectorRepository.ChunkWithVector(
                        "Mentored two junior developers.", "experience", new float[]{0f, 1f, 0.1f})))
                .indexFor(RESUME_ID);

        List<SemanticEvidenceMatcher.Index.Hit> hits = index.rank("Experience with Docker", 3);

        assertEquals(1, hits.size(), "only the containerised passage is close in meaning to Docker");
        assertTrue(hits.get(0).similarity() > 0.9);
        assertTrue(hits.get(0).text().contains("Containerized"));
    }

    @Test
    @DisplayName("A passage below the relevance bar is not returned at all")
    void anIrrelevantPassageIsNotReturned() {
        SemanticEvidenceMatcher.Index index = matcher(List.of(
                new ChunkVectorRepository.ChunkWithVector(
                        "Mentored two junior developers.", "experience", new float[]{0f, 1f, 0.1f})))
                .indexFor(RESUME_ID);

        // Silence has to be an available answer. Without a threshold the nearest
        // chunk to any requirement is always returned, and a model handed it will
        // describe it as evidence - which is how "participate in code reviews"
        // acquires confident non-evidence.
        assertTrue(index.rank("Experience with Docker", 3).isEmpty());
    }

    @Test
    @DisplayName("Every requirement is embedded in a single batched call")
    void requirementsAreEmbeddedInOneBatch() {
        int[] batchSizes = {0};
        EmbeddingProvider counting = new EmbeddingProvider() {
            @Override
            public float[] embed(String text) {
                return embedBatch(List.of(text)).get(0);
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                batchSizes[0]++;
                return texts.stream().map(t -> new float[]{0f, 0f, 1f}).toList();
            }
        };

        SemanticEvidenceMatcher.Index index = new ResumeSemanticEvidenceMatcher(
                new ChunkVectorRepository(null) {
                    @Override
                    public List<ChunkWithVector> findWithEmbeddings(UUID resumeId) {
                        return List.of(new ChunkVectorRepository.ChunkWithVector("text", "s",
                                new float[]{0f, 0f, 1f}));
                    }
                },
                new com.resumerag.embedding.EmbeddingService(counting),
                new com.resumerag.analysis.scoring.ScoringConfiguration())
                .indexFor(RESUME_ID);

        index.rankAll(List.of("one", "two", "three", "four", "five"), 3);

        assertEquals(1, batchSizes[0],
                "one embedding round trip per requirement is what turns a fast analysis into a slow one; "
                        + "a job description has tens of requirements");
    }

    @Test
    @DisplayName("A vector dimension mismatch is skipped rather than compared")
    void aDimensionMismatchIsSkipped() {
        SemanticEvidenceMatcher.Index index = matcher(List.of(
                new ChunkVectorRepository.ChunkWithVector("old resume", "experience", new float[]{1f, 0f}),
                new ChunkVectorRepository.ChunkWithVector("current resume", "experience",
                        new float[]{1f, 0f, 0.1f})))
                .indexFor(RESUME_ID);

        List<SemanticEvidenceMatcher.Index.Hit> hits = index.rank("Experience with Docker", 3);

        // Comparing across dimensions would produce a number that looks like a
        // similarity and means nothing.
        assertEquals(1, hits.size());
        assertEquals("current resume", hits.get(0).text());
    }

    @Test
    @DisplayName("An embedding failure yields no hits rather than an exception")
    void anEmbeddingFailureYieldsNoHits() {
        EmbeddingProvider failing = new EmbeddingProvider() {
            @Override
            public float[] embed(String text) {
                throw new IllegalStateException("embedding model not loaded");
            }

            @Override
            public List<float[]> embedBatch(List<String> texts) {
                throw new IllegalStateException("embedding model not loaded");
            }
        };

        SemanticEvidenceMatcher.Index index = new ResumeSemanticEvidenceMatcher(
                new ChunkVectorRepository(null) {
                    @Override
                    public List<ChunkWithVector> findWithEmbeddings(UUID resumeId) {
                        return List.of(new ChunkVectorRepository.ChunkWithVector("text", "s",
                                new float[]{1f, 0f, 0.1f}));
                    }
                },
                new com.resumerag.embedding.EmbeddingService(failing),
                new com.resumerag.analysis.scoring.ScoringConfiguration())
                .indexFor(RESUME_ID);

        // Losing semantic recall costs accuracy and must not cost the analysis.
        assertTrue(index.rankAll(List.of("a", "b"), 3).stream().allMatch(List::isEmpty));
    }
}
