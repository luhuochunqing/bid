package com.xiyu.bid.casework.domain.policy;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * 文本相似度计算纯核心策略。
 *
 * <p>统一的分词、前缀剥离与相似度计算工具，避免在多个策略类中重复实现。
 */
public final class TextSimilarityPolicy {

    private TextSimilarityPolicy() {}

    /**
     * 对文本进行分词，返回 token 集合。
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
     * 针对招投标评分条款的增强分词（含编号剥离与 n-gram 抽取）。
     */
    public static Set<String> extractTokens(String text) {
        if (!hasText(text)) {
            return Collections.emptySet();
        }

        Set<String> tokens = new LinkedHashSet<>();
        String[] rawWords = text.replaceAll("[\\p{Punct}\\p{Space}\\u3000-\\u303F\\uFF00-\\uFFEF]+", " ").trim().split("\\s+");
        for (String word : rawWords) {
            String cleanWord = cleanTitle(word);
            if (cleanWord.length() >= 2 && cleanWord.length() <= 8) {
                tokens.add(cleanWord);
            }
            if (cleanWord.matches(".*[\\u4e00-\\u9fa5].*")) {
                for (int i = 0; i <= cleanWord.length() - 2; i++) {
                    tokens.add(cleanWord.substring(i, i + 2));
                }
                for (int i = 0; i <= cleanWord.length() - 3; i++) {
                    tokens.add(cleanWord.substring(i, i + 3));
                }
            }
        }
        return tokens;
    }

    /**
     * 计算两个 token 集合的 Jaccard 相似度。
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

    /**
     * 剥离前缀编号（支持多层级数字、中英文章节、带括号序号、罗马数字、特殊序号等），提取纯净业务名称。
     */
    public static String cleanTitle(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = text.trim();
        String prev;
        int maxIter = 5;
        do {
            prev = cleaned;
            cleaned = cleaned.replaceAll("^(第[0-9一二三四五六七八九十百\\._]+[章节条款项部分节]+|附[件表]+[0-9一二三四五六七八九十\\-_\\.]*|表[0-9\\-_\\.]+)[、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[A-Za-z0-9]+([\\.\\-_][A-Za-z0-9]+)*([\\(（][0-9一二三四五六七八九十a-zA-Z]+[\\)）])?[、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[\\(（\\[【][0-9一二三四五六七八九十a-zA-Z]+[\\)）\\]】][、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[①②③④⑤⑥⑦⑧⑨⑩㈠㈡㈢㈣㈤㈥㈦㈧㈨㈩ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩ][、\\.\\s:：\\-_]*", "");
            cleaned = cleaned.replaceAll("^[一二三四五六七八九十]+[、\\.\\s:：\\-_]+", "");
            cleaned = cleaned.trim();
            maxIter--;
        } while (!cleaned.equals(prev) && !cleaned.isEmpty() && maxIter > 0);

        return cleaned.isEmpty() ? text.trim() : cleaned;
    }
}
