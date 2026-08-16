// Input: 投标文件全文与待打分 ScoreItem
// Output: 最相关的文本摘录（控制在最大字符预算内）
// Pos: scoreparse/application — 纯业务算法，无框架依赖
// 维护声明: 维护者按项目SOP；从 ScoreScoringAppService 拆出以满足 300 行预算
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.entity.ScoreItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import java.util.regex.Pattern;

/**
 * 投标文件评分相关段落提取器（FR-053）。
 *
 * <p>基于评分项维度与细则关键词对全文段落打分并提取最相关的段落片段，避免一刀切硬截断。
 */
public final class ScoreDocExcerptExtractor {

    private static final Pattern CONDITION_SCORE_PATTERN = Pattern.compile(
            "(?:满足|提供|具备|每|若|如|未提供|不满足|凡|符合|按|出现|配置)[^\\n。；;]{1,60}?(?:得|计|加|扣|减|按|给)\\s*\\d+(?:\\.\\d+)?\\s*分" +
            "|\\d+(?:\\.\\d+)?\\s*分[^\\n。；;]{1,40}?(?:满足|提供|具备|要求|标准|条件)");

    private ScoreDocExcerptExtractor() {
    }

    public static String extractRelevantExcerpt(String fullText, ScoreItem item, int maxChars) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }
        if (fullText.length() <= maxChars) {
            return fullText;
        }

        List<String> keywords = new ArrayList<>();
        if (item.getDim() != null && !item.getDim().isBlank()) {
            keywords.add(item.getDim());
        }
        if (item.getDetail() != null) {
            for (String token : item.getDetail().split("[^\\p{IsHan}a-zA-Z0-9]+")) {
                if (token.length() >= 2 && !token.matches("\\d+")) {
                    keywords.add(token);
                }
            }
        }

        String[] paragraphs = fullText.split("\n{2,}");
        List<ScoredParagraph> scoredList = new ArrayList<>();
        for (int i = 0; i < paragraphs.length; i++) {
            String p = paragraphs[i].trim();
            if (p.isEmpty()) {
                continue;
            }
            int score = 0;
            for (String kw : keywords) {
                if (p.contains(kw)) {
                    score += kw.length();
                }
            }
            scoredList.add(new ScoredParagraph(i, p, score));
        }

        List<ScoredParagraph> topHits = scoredList.stream()
                .filter(sp -> sp.score > 0)
                .sorted(Comparator.comparingInt((ScoredParagraph sp) -> sp.score).reversed())
                .limit(10)
                .toList();

        Set<Integer> selectedIndices = new TreeSet<>();
        if (topHits.isEmpty()) {
            for (int i = 0; i < Math.min(5, paragraphs.length); i++) {
                selectedIndices.add(i);
            }
            for (int i = Math.max(0, paragraphs.length - 5); i < paragraphs.length; i++) {
                selectedIndices.add(i);
            }
        } else {
            for (ScoredParagraph sp : topHits) {
                selectedIndices.add(sp.index);
                if (sp.index > 0) {
                    selectedIndices.add(sp.index - 1);
                }
                if (sp.index < paragraphs.length - 1) {
                    selectedIndices.add(sp.index + 1);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int idx : selectedIndices) {
            if (sb.length() + paragraphs[idx].length() > maxChars) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n---\n\n");
            }
            sb.append(paragraphs[idx]);
        }
        return !sb.isEmpty() ? sb.toString() : fullText.substring(0, Math.min(fullText.length(), maxChars));
    }

    /** 提取包含条件→得分语义特征的段落切片（用于四路召回之召回三，不依赖显式「评分标准」关键词） */
    public static List<String> extractSemanticScoreParagraphs(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        String[] paragraphs = fullText.split("\n{2,}");
        for (String p : paragraphs) {
            String trimmed = p.trim();
            if (trimmed.length() >= 10 && CONDITION_SCORE_PATTERN.matcher(trimmed).find()) {
                result.add(trimmed);
            }
        }
        return result;
    }

    private record ScoredParagraph(int index, String text, int score) {}
}
