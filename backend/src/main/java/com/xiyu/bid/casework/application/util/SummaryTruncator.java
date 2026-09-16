package com.xiyu.bid.casework.application.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 应答片段摘要的语义截断工具。
 * 纯函数，无状态，无线程安全问题。
 */
public final class SummaryTruncator {

    private static final Pattern SEQUENCE_MARKER = Pattern.compile(
            "(?:\\d+\\.|[一二三四五六七八九十百]+、|[①②③④⑤⑥⑦⑧⑨⑩]|[（(](?:\\d+|[一二三四五六七八九十百]+)[）)])");

    private SummaryTruncator() {}

    /**
     * 按语义断点截断文本。
     *
     * <p>断点优先序：段落 \n\n > 换行 \n > 句子 。！？!? > 序号安全回退。
     * 不在序号与后续内容之间截断（如 "1. xxx" 不拆为 "1."）。
     * 当前实现保留“窗口内最后一个安全断点”策略，序号保护仅作为无安全断点时的回退；
     * 这样可保持既有语义断点测试的结果，同时确保硬切不会留下裸序号。
     *
     * @param text      原始文本（可为 null）
     * @param maxLength 截断长度上限
     * @return 截断后文本（未截断则返回原文）；若截断则追加 "..."
     */
    // REUSE-OK: backend/src/main/java/com/xiyu/bid/common/util/StringUtils.java — StringUtils.truncate 为按字符数硬截断（无断点语义/序号保护），本方法提供语义断点与序号安全，行为不同，属必要新增
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;

        String candidate = text.substring(0, maxLength);

        int p1 = lastParagraphBreak(text, maxLength);
        int p2 = lastLineBreak(text, maxLength);
        int p3 = lastSafeSentenceBreak(text, maxLength);

        int best = Math.max(p1, Math.max(p2, p3));

        if (best < 0) {
            return cutBeforeTruncatedSequence(candidate, maxLength, text) + "...";
        }

        return text.substring(0, best + 1).trim() + "...";
    }

    private static String cutBeforeTruncatedSequence(String candidate, int maxLength, String text) {
        Matcher matcher = SEQUENCE_MARKER.matcher(text);
        int safeEnd = maxLength;
        while (matcher.find() && matcher.start() < maxLength) {
            String marker = matcher.group();
            String window = candidate.substring(0, Math.min(candidate.length(), maxLength)).trim();
            boolean markerCut = matcher.end() >= maxLength || window.endsWith(marker);
            if (markerCut) {
                safeEnd = Math.min(safeEnd, matcher.start());
            }
        }
        return text.substring(0, safeEnd).trim();
    }

    private static int lastParagraphBreak(String text, int maxLength) {
        for (int i = maxLength - 1; i >= 1; i--) {
            if (text.charAt(i) == '\n' && text.charAt(i - 1) == '\n') {
                return i - 1;
            }
            if (text.charAt(i) == '\n' && i >= 3
                    && text.charAt(i - 1) == '\r'
                    && text.charAt(i - 2) == '\n'
                    && text.charAt(i - 3) == '\r') {
                return i - 3;
            }
        }
        return -1;
    }

    private static int lastLineBreak(String text, int maxLength) {
        for (int i = maxLength - 1; i >= 0; i--) {
            if (text.charAt(i) == '\n') {
                return i;
            }
        }
        return -1;
    }

    private static int lastSafeSentenceBreak(String text, int maxLength) {
        for (int i = maxLength - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (!isSentencePunct(c)) continue;
            if (c == '.' && isPartOfSequenceMarker(text, i)) continue;
            return i;
        }
        return -1;
    }

    private static boolean isSentencePunct(char c) {
        return c == '。' || c == '！' || c == '？'
                || c == '.' || c == '!' || c == '?';
    }

    private static boolean isPartOfSequenceMarker(String text, int dotPos) {
        if (dotPos == 0) return false;
        int j = dotPos - 1;
        if (!Character.isDigit(text.charAt(j))) return false;
        while (j > 0 && Character.isDigit(text.charAt(j - 1))) {
            j--;
        }
        return j == 0 || !Character.isDigit(text.charAt(j - 1));
    }
}
