package com.resumerag.algorithm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosineSimilarityTest {

    private static final double EPSILON = 1e-9;

    @Test
    void identicalVectorsScoreOne() {
        float[] a = {1f, 2f, 3f};
        float[] b = {1f, 2f, 3f};

        assertEquals(1.0, CosineSimilarity.of(a, b), EPSILON);
    }

    @Test
    void orthogonalVectorsScoreZero() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};

        assertEquals(0.0, CosineSimilarity.of(a, b), EPSILON);
    }

    @Test
    void oppositeVectorsScoreMinusOne() {
        float[] a = {1f, 2f, 3f};
        float[] b = {-1f, -2f, -3f};

        assertEquals(-1.0, CosineSimilarity.of(a, b), EPSILON);
    }

    @Test
    void isInvariantToMagnitude() {
        float[] a = {1f, 2f, 3f};
        float[] b = {10f, 20f, 30f};

        assertEquals(1.0, CosineSimilarity.of(a, b), EPSILON);
    }

    @Test
    void symmetricKnownValue() {
        float[] a = {1f, 1f};
        float[] b = {1f, 0f};

        assertEquals(Math.sqrt(0.5), CosineSimilarity.of(a, b), EPSILON);
        assertEquals(CosineSimilarity.of(a, b), CosineSimilarity.of(b, a), EPSILON);
    }

    @Test
    void zeroVectorScoresZeroRatherThanNaN() {
        assertEquals(0.0, CosineSimilarity.of(new float[]{0f, 0f}, new float[]{1f, 2f}), EPSILON);
        assertEquals(0.0, CosineSimilarity.of(new float[]{1f, 2f}, new float[]{0f, 0f}), EPSILON);
        assertEquals(0.0, CosineSimilarity.of(new float[]{0f, 0f}, new float[]{0f, 0f}), EPSILON);
    }

    @Test
    void rejectsMismatchedLengths() {
        assertThrows(IllegalArgumentException.class,
                () -> CosineSimilarity.of(new float[]{1f, 2f}, new float[]{1f}));
    }

    @Test
    void staysWithinBoundsFor768DimensionalEmbeddings() {
        float[] a = new float[768];
        float[] b = new float[768];
        for (int i = 0; i < 768; i++) {
            a[i] = (float) Math.sin(i);
            b[i] = (float) Math.cos(i);
        }

        double score = CosineSimilarity.of(a, b);

        assertTrue(score >= -1.0 && score <= 1.0, "cosine must stay in [-1, 1] but was " + score);
    }

    @Test
    void emptyVectorsScoreZero() {
        assertEquals(0.0, CosineSimilarity.of(new float[0], new float[0]), EPSILON);
    }
}
