package com.xiyu.bid.casework.domain.policy;

/**
 * 纯核心：向量余弦相似度计算策略。
 *
 * <p>无 Spring 依赖、无状态、无副作用。</p>
 */
public final class CosineSimilarityPolicy {

    private CosineSimilarityPolicy() {
        // utility class
    }

    /**
     * 计算两个 float 向量的余弦相似度。
     *
     * @param a 向量 a
     * @param b 向量 b
     * @return 相似度，范围 [-1, 1]；输入非法时返回 -1
     */
    public static double compute(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) {
            return -1.0d;
        }

        double dot = 0.0d;
        double normA = 0.0d;
        double normB = 0.0d;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0d || normB == 0.0d) {
            return 0.0d;
        }

        double similarity = dot / (Math.sqrt(normA) * Math.sqrt(normB));
        return Math.max(-1.0d, Math.min(1.0d, similarity));
    }
}
