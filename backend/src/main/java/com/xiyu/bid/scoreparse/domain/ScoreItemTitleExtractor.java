// Input: 评分细则原文
// Output: 首行有原文依据的末级标题
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts a short terminal title from the first line without rewriting evidence. */
public final class ScoreItemTitleExtractor {

    private static final Pattern NUMBER_PREFIX = Pattern.compile(
            "^\\s*(?:第\\s*[一二三四五六七八九十百0-9]+[章节项条]?\\s*|[（(]\\s*\\d+(?:\\.\\d+)*\\s*[）)]\\s*|(?:\\d+(?:\\.\\d+)*|[A-Za-z]\\d+(?:[.-]\\d+)*)[、.)）]\\s*)");
    private static final Pattern COLON = Pattern.compile("^\\s*(.+?)\\s*[：:]\\s*(.*)$");
    private static final Pattern PUNCTUATED_SENTENCE = Pattern.compile("[。；;]");
    private static final int MAX_TITLE_LENGTH = 40;

    private ScoreItemTitleExtractor() {
    }

    /**
     * Returns the title before the first colon when that prefix is a short title.
     * The matched prefix must be literal text from the original first line; only
     * numbering and the trailing colon are removed from the returned title.
     */
    public static Optional<String> extract(String detail) {
        if (detail == null || detail.isBlank()) {
            return Optional.empty();
        }
        String firstLine = firstLine(detail);
        Matcher matcher = COLON.matcher(firstLine);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String rawPrefix = matcher.group(1).trim();
        String contentAfterColon = matcher.group(2).trim();
        if (rawPrefix.isEmpty() || rawPrefix.length() > MAX_TITLE_LENGTH
                || PUNCTUATED_SENTENCE.matcher(rawPrefix).find()
                || contentAfterColon.isEmpty()) {
            return Optional.empty();
        }
        String title = NUMBER_PREFIX.matcher(rawPrefix).replaceFirst("").trim();
        return title.isEmpty() ? Optional.empty() : Optional.of(title);
    }

    private static String firstLine(String detail) {
        String normalized = detail.replaceAll("(?i)<br\\s*/?>", "\n");
        int newline = normalized.indexOf('\n');
        return newline < 0 ? normalized.trim() : normalized.substring(0, newline).trim();
    }
}
