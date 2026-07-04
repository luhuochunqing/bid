package com.xiyu.bid.casework.domain.policy;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文本相似度计算纯核心策略。
 *
 * <p>统一的分词与相似度计算工具，避免在多个类中重复实现。
 */
public final class TextSimilarityPolicy {

    private TextSimilarityPolicy() {}

    /**
     * 对文本进行分词，返回 token 集合。
     *
     * <p>策略：
     * <ul>
     *   <li>按空白符和中文标点切分</li>
     *   <li>英文/数字词：保留长度 ≥ 2 的完整词</li>
     *   <li>中文词：做 bigram（相邻两字）切分</li>
     *   <li>统一转小写</li>
     * </ul>
     */
    public static Set<String> tokenize(String text) {
        Set<String> tokens = new HashSet<>();
        if (text == null || text.isBlank()) {
            return tokens;
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        for (String segment : normalized.split("\\s+|[，。、；：！？\"'（）【】]")) {
            String trimmed = segment.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!containsCjk(trimmed) && trimmed.length() >= 2) {
                tokens.add(trimmed);
            } else {
                for (int i = 0; i < trimmed.length() - 1; i++) {
                    tokens.add(trimmed.substring(i, i + 2));
                }
            }
        }
        return tokens;
    }

    /**
     * 计算两个 token 集合的 Jaccard 相似度。
     *
     * @return 0.0 ~ 1.0 之间的相似度，任一集合为空时返回 0.0
     */
    public static double jaccardSimilarity(Set<String> a, Set<String> b) {
        if (a == null || b == null || a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        int intersectionSize = intersection.size();
        if (intersectionSize == 0) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        return (double) intersectionSize / union.size();
    }

    /**
     * 计算两个文本的 Jaccard 相似度（自动分词）。
     */
    public static double jaccardSimilarityOfText(String textA, String textB) {
        return jaccardSimilarity(tokenize(textA), tokenize(textB));
    }

    /**
     * 判断字符串是否有实际文本内容（非 null 且非空白）。
     */
    public static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    /**
     * 判断文本是否包含中日韩统一表意文字。
     */
    public static boolean containsCjk(String text) {
        return text.codePoints().anyMatch(cp -> {
            Character.UnicodeBlock block = Character.UnicodeBlock.of(cp);
            return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                    || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS;
        });
    }
}
