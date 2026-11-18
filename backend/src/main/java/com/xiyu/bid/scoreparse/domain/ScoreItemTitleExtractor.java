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
    private static final Pattern SCORE_SUFFIX = Pattern.compile(
            "\\s*[（(](?:满分|最高)?\\s*\\d+(?:\\.\\d+)?\\s*分[）)]\\s*$");
    private static final Pattern COLON = Pattern.compile("^\\s*(.+?)\\s*[：:]\\s*(.*)$");
    private static final Pattern PUNCTUATED_SENTENCE = Pattern.compile("[。；;]");
    private static final Pattern RULE_VERB_PREFIX = Pattern.compile(
            "^\\s*(?:根据|若|如|当|按|投标人|对|由|评标委员会|如果|凡|自|提供|具备).*");
    private static final Pattern SCORE_PREDICATE = Pattern.compile(
            "(?:得|加|扣)\\s*\\d+(?:\\.\\d+)?\\s*分|不得分|满分");
    private static final int MAX_TITLE_LENGTH = 40;

    private ScoreItemTitleExtractor() {
    }

    /**
     * Returns the title before the first colon or from a heading-style first line.
     * The matched prefix must be literal text from the original text.
     */
    public static Optional<String> extract(String detail) {
        if (detail == null || detail.isBlank()) {
            return Optional.empty();
        }
        String normalized = detail.replaceAll("(?i)<br\\s*/?>", "\n");
        String[] lines = normalized.split("\n");
        String firstLine = lines[0].trim();

        Matcher matcher = COLON.matcher(firstLine);
        if (matcher.matches()) {
            String rawPrefix = matcher.group(1).trim();
            String contentAfterColon = matcher.group(2).trim();
            if (contentAfterColon.isEmpty() && lines.length > 1) {
                for (int i = 1; i < lines.length; i++) {
                    if (!lines[i].isBlank()) {
                        contentAfterColon = lines[i].trim();
                        break;
                    }
                }
            }
            if (!rawPrefix.isEmpty() && rawPrefix.length() <= MAX_TITLE_LENGTH
                    && !PUNCTUATED_SENTENCE.matcher(rawPrefix).find()
                    && !contentAfterColon.isEmpty()) {
                String title = NUMBER_PREFIX.matcher(rawPrefix).replaceFirst("").trim();
                if (!title.isEmpty()) {
                    return Optional.of(title);
                }
            }
        }

        // When there is no colon, but there is body content following line 1:
        if (lines.length > 1) {
            String candidate = firstLine;
            candidate = SCORE_SUFFIX.matcher(candidate).replaceFirst("").trim();
            candidate = NUMBER_PREFIX.matcher(candidate).replaceFirst("").trim();

            // If line 1 was wrapped across lines due to table column width (e.g. "能够提供最长" + "账期")
            if (!candidate.isEmpty() && candidate.length() <= 15
                    && !PUNCTUATED_SENTENCE.matcher(candidate).find()
                    && !SCORE_PREDICATE.matcher(candidate).find()
                    && lines.length > 2) {
                String secondLine = lines[1].trim();
                secondLine = SCORE_SUFFIX.matcher(secondLine).replaceFirst("").trim();
                if (!secondLine.isEmpty() && secondLine.length() <= 6
                        && !PUNCTUATED_SENTENCE.matcher(secondLine).find()
                        && !SCORE_PREDICATE.matcher(secondLine).find()) {
                    candidate = candidate + secondLine;
                }
            }

            if (!candidate.isEmpty() && candidate.length() <= MAX_TITLE_LENGTH
                    && !PUNCTUATED_SENTENCE.matcher(candidate).find()
                    && !RULE_VERB_PREFIX.matcher(candidate).matches()
                    && !SCORE_PREDICATE.matcher(candidate).find()) {
                boolean hasRemainingBody = false;
                for (int i = 1; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty() && !line.equals(candidate)) {
                        hasRemainingBody = true;
                        break;
                    }
                }
                if (hasRemainingBody) {
                    return Optional.of(candidate);
                }
            }
        }

        return Optional.empty();
    }
}
