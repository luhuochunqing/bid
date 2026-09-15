package com.xiyu.bid.casework.application.util;

/**
 * 应答片段摘要的语义截断工具。
 * 纯函数，无状态，无线程安全问题。
 */
public final class SummaryTruncator {

    private SummaryTruncator() {}

    /**
     * 按语义断点截断文本。
     *
     * <p>断点优先序：段落 \n\n > 换行 \n > 句子 。！？!? > 序号安全回退。
     * 不在序号与后续内容之间截断（如 "1. xxx" 不拆为 "1."）。
     *
     * @param text      原始文本（可为 null）
     * @param maxLength 截断长度上限
     * @return 截断后文本（未截断则返回原文）；若截断则追加 "..."
     */
    public static String truncate(String text, int maxLength) {
        if (text == null) return "";
        if (text.length() <= maxLength) return text;

        String candidate = text.substring(0, maxLength);

        int p1 = lastParagraphBreak(text, maxLength);
        int p2 = lastLineBreak(text, maxLength);
        int p3 = lastSafeSentenceBreak(text, maxLength);

        int best = Math.max(p1, Math.max(p2, p3));

        if (best < 0) {
            return candidate.trim() + "...";
        }

        return text.substring(0, best + 1).trim() + "...";
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
