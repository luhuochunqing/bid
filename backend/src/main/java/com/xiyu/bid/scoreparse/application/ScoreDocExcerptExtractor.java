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
import java.util.regex.Matcher;

/**
 * 投标文件评分相关段落提取器（FR-053）。
 *
 * <p>基于评分项维度与细则关键词对全文段落打分并提取最相关的段落片段，避免一刀切硬截断。
 */
public final class ScoreDocExcerptExtractor {

    private static final Pattern CONDITION_SCORE_PATTERN = Pattern.compile(
            "(?:满足|提供|具备|每|若|如|未提供|不满足|凡|符合|按|出现|配置)[^\\n。；;]{1,60}?(?:得|计|加|扣|减|按|给)\\s*\\d+(?:\\.\\d+)?\\s*分" +
            "|\\d+(?:\\.\\d+)?\\s*分[^\\n。；;]{1,40}?(?:满足|提供|具备|要求|标准|条件)");

    /** Word 自动目录锚点段：形如 [标题 页码](#_Toc232494505)，只有导航没有正文证据，打分与拼接均排除（spec 049 验收发现） */
    private static final Pattern TOC_ANCHOR_PATTERN = Pattern.compile("\\[[^\\]]*\\]\\(\\s*#_Toc[^)]*\\)");

    private ScoreDocExcerptExtractor() {
    }

    public static String extractRelevantExcerpt(String fullText, ScoreItem item, int maxChars) {
        if (item == null) {
            return "";
        }
        return extractRelevantExcerpt(fullText, item.getDim(), item.getDetail(), maxChars);
    }

    /** dim/detail 重载：供案例沉淀链路直接传评分维度与细则关键词，避免跨模块依赖 ScoreItem */
    public static String extractRelevantExcerpt(String fullText, String dim, String detail, int maxChars) {
        return extractRelevantExcerptWithSegments(fullText, dim, detail, maxChars).text();
    }

    public static Excerpt extractRelevantExcerptWithSegments(
            String fullText, String dim, String detail, int maxChars) {
        if (fullText == null || fullText.isBlank()) {
            return new Excerpt("", List.of());
        }
        List<Paragraph> paragraphs = scanParagraphs(fullText);
        if (fullText.length() <= maxChars) {
            return new Excerpt(fullText, toSegments(paragraphs.stream()
                    .filter(paragraph -> !paragraph.text().isEmpty())
                    .toList()));
        }

        List<String> keywords = new ArrayList<>();
        if (dim != null && !dim.isBlank()) {
            keywords.add(dim);
        }
        if (detail != null) {
            for (String token : detail.split("[^\\p{IsHan}a-zA-Z0-9]+")) {
                if (token.length() >= 2 && !token.matches("\\d+")) {
                    keywords.add(token);
                }
            }
        }

        List<ScoredParagraph> scoredList = new ArrayList<>();
        for (int i = 0; i < paragraphs.size(); i++) {
            String p = paragraphs.get(i).text();
            if (p.isEmpty() || isTocAnchor(p)) {
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
            int picked = 0;
            for (int i = 0; i < paragraphs.size() && picked < 5; i++) {
                if (!isTocAnchor(paragraphs.get(i).text())) {
                    selectedIndices.add(i);
                    picked++;
                }
            }
            picked = 0;
            for (int i = paragraphs.size() - 1; i >= 0 && picked < 5; i--) {
                if (!isTocAnchor(paragraphs.get(i).text())) {
                    selectedIndices.add(i);
                    picked++;
                }
            }
        } else {
            for (ScoredParagraph sp : topHits) {
                selectedIndices.add(sp.index);
                if (sp.index > 0 && !isTocAnchor(paragraphs.get(sp.index - 1).text())) {
                    selectedIndices.add(sp.index - 1);
                }
                if (sp.index < paragraphs.size() - 1 && !isTocAnchor(paragraphs.get(sp.index + 1).text())) {
                    selectedIndices.add(sp.index + 1);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        List<Paragraph> selected = new ArrayList<>();
        for (int idx : selectedIndices) {
            Paragraph paragraph = paragraphs.get(idx);
            if (sb.length() + paragraph.text().length() > maxChars) {
                break;
            }
            if (!sb.isEmpty()) {
                sb.append("\n\n---\n\n");
            }
            sb.append(paragraph.text());
            selected.add(paragraph);
        }
        if (!sb.isEmpty()) {
            return new Excerpt(sb.toString(), toSegments(selected));
        }
        if (selectedIndices.isEmpty()) {
            // 全部段落都是 TOC 锚点（无正文证据）——诚实返回空，由调用方跳过，不把目录当证据
            return new Excerpt("", List.of());
        }
        String fallback = fullText.substring(0, Math.min(fullText.length(), maxChars));
        return new Excerpt(fallback, List.of(new Segment("segment-0", fallback, 0)));
    }

    private static List<Paragraph> scanParagraphs(String fullText) {
        List<Paragraph> paragraphs = new ArrayList<>();
        Matcher separator = Pattern.compile("\n{2,}").matcher(fullText);
        int start = 0;
        int index = 0;
        while (separator.find()) {
            paragraphs.add(paragraph(index++, fullText, start, separator.start()));
            start = separator.end();
        }
        paragraphs.add(paragraph(index, fullText, start, fullText.length()));
        return paragraphs;
    }

    private static Paragraph paragraph(int index, String fullText, int rawStart, int rawEnd) {
        int start = rawStart;
        int end = rawEnd;
        while (start < end && Character.isWhitespace(fullText.charAt(start))) {
            start++;
        }
        while (end > start && Character.isWhitespace(fullText.charAt(end - 1))) {
            end--;
        }
        return new Paragraph(index, fullText.substring(start, end), start);
    }

    private static List<Segment> toSegments(List<Paragraph> paragraphs) {
        List<Segment> segments = new ArrayList<>();
        for (Paragraph paragraph : paragraphs) {
            segments.add(new Segment("segment-" + segments.size(), paragraph.text(), paragraph.sourceStartOffset()));
        }
        return List.copyOf(segments);
    }

    /** 是否为 Word 自动目录锚点段（只有导航、无正文证据） */
    private static boolean isTocAnchor(String paragraph) {
        return paragraph != null && TOC_ANCHOR_PATTERN.matcher(paragraph).find();
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

    public record Excerpt(String text, List<Segment> segments) {}

    public record Segment(String id, String text, int sourceStartOffset) {}

    private record Paragraph(int index, String text, int sourceStartOffset) {}

    private record ScoredParagraph(int index, String text, int score) {}
}
