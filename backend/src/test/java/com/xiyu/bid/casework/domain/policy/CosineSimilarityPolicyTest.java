package com.xiyu.bid.casework.domain.policy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("CosineSimilarityPolicy")
class CosineSimilarityPolicyTest {

    @Test
    void identicalVectors_shouldReturnOne() {
        float[] v = {1.0f, 2.0f, 3.0f};
        assertEquals(1.0d, CosineSimilarityPolicy.compute(v, v), 1e-6);
    }

    @Test
    void orthogonalVectors_shouldReturnZero() {
        float[] a = {1.0f, 0.0f, 0.0f};
        float[] b = {0.0f, 1.0f, 0.0f};
        assertEquals(0.0d, CosineSimilarityPolicy.compute(a, b), 1e-6);
    }

    @Test
    void oppositeVectors_shouldReturnMinusOne() {
        float[] a = {1.0f, 2.0f, 3.0f};
        float[] b = {-1.0f, -2.0f, -3.0f};
        assertEquals(-1.0d, CosineSimilarityPolicy.compute(a, b), 1e-6);
    }

    @Test
    void nullInput_shouldReturnNegativeOne() {
        float[] v = {1.0f, 0.0f};
        assertEquals(-1.0d, CosineSimilarityPolicy.compute(null, v), 1e-9);
        assertEquals(-1.0d, CosineSimilarityPolicy.compute(v, null), 1e-9);
    }

    @Test
    void mismatchedLengths_shouldReturnNegativeOne() {
        float[] a = {1.0f, 0.0f};
        float[] b = {1.0f, 0.0f, 0.0f};
        assertEquals(-1.0d, CosineSimilarityPolicy.compute(a, b), 1e-9);
    }

    @Test
    void zeroVector_shouldReturnZero() {
        float[] a = {0.0f, 0.0f, 0.0f};
        float[] b = {1.0f, 0.0f, 0.0f};
        assertEquals(0.0d, CosineSimilarityPolicy.compute(a, b), 1e-9);
    }
}
