// Input: 评分细则原文
// Output: 首行有原文依据的末级标题
// Pos: scoreparse/domain — 纯核心，无框架依赖
package com.xiyu.bid.scoreparse.domain;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.xiyu.bid.scoreparse.domain.ScoreCategoryClassifier.stripTrailingScore;

/** Extracts a short terminal title from the first line without rewriting evidence. */
public final class ScoreItemTitleExtractor {

    private static final Pattern NUMBER_PREFIX = Pattern.compile(
            "^\\s*(?:第\\s*[一二三四五六七八九十百0-9]+[章节项条]?\\s*|[（(]\\s*\\d+(?:\\.\\d+)*\\s*[）)]\\s*|(?:\\d+(?:\\.\\d+)*|[A-Za-z]\\d+(?:[.-]\\d+)*)[、.)）]\\s*)");
    private static final Pattern SPLIT_SCORE_NEWLINE = Pattern.compile(
            "([（(](?:满分|最高)?\\s*\\d+(?:\\.\\d+)?)\\s*\\n\\s*(分[）)])");
    private static final Pattern UNCLOSED_SCORE_SUFFIX = Pattern.compile(
            "\\s*[（(](?:满分|最高)?\\s*\\d+(?:\\.\\d+)?\\s*分?\\s*$");
    private static final Pattern PURE_WEIGHT_CELL = Pattern.compile(
            "^[~≈]?\\s*\\d+(?:\\.\\d+)?\\s*(?:分|分值|%)?\\s*$");
    private static final Pattern HTML_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern COLON = Pattern.compile("^\\s*(.+?)\\s*[：:]\\s*(.*)$");
    private static final Pattern PUNCTUATED_SENTENCE = Pattern.compile("[。；;]");
    private static final Pattern RULE_VERB_PREFIX = Pattern.compile(
            "^\\s*(?:根据|若|如|当|按|投标人|对|由|评标委员会|如果|凡|自|提供|具备).*");
    private static final Pattern SCORE_PREDICATE = Pattern.compile(
            "(?:得|加|扣)\\s*\\d+(?:\\.\\d+)?\\s*分|不得分|满分");
    private static final int MAX_TITLE_LENGTH = 40;
    private static final int LEGACY_FALLBACK_LENGTH = 50;

    private ScoreItemTitleExtractor() {
    }

    public static String resolveCandidateDimension(ScoreCandidate candidate, boolean preferCandidateName) {
        String dimension = candidate.dim() == null ? "" : candidate.dim().trim();
        String detail = candidate.detail();
        boolean validDimension = !ScoreCategoryClassifier.isInvalidOrPlaceholderName(dimension)
                && !isLegacyRuleBodyName(dimension, detail)
                && !isLegacyRuleBodyName(dimension, candidate.sourceText());
        String limitedDimension = dimension.length() > 200 ? dimension.substring(0, 200) : dimension;
        if (validDimension && preferCandidateName) {
            return limitedDimension;
        }

        Optional<String> title = extract(detail);
        if (title.isPresent()) {
            return title.get();
        }
        if (ScoreCategoryClassifier.hasUnclosedBracket(dimension)) {
            return "";
        }
        if (dimension.contains("|")) {
            String cleaned = cleanDimensionLabel(dimension);
            if (!ScoreCategoryClassifier.needsSpecificTitle(cleaned)
                    && !isLegacyRuleBodyName(cleaned, detail)
                    && !isLegacyRuleBodyName(cleaned, candidate.sourceText())) {
                return cleaned.length() > 200 ? cleaned.substring(0, 200) : cleaned;
            }
        }
        if (!validDimension) {
            return "";
        }
        if (!ScoreCategoryClassifier.needsSpecificTitle(dimension) && !candidate.isParentOnlyDim()) {
            return limitedDimension;
        }
        if (detail != null && !detail.isBlank()) {
            String trimmed = normalizeRuleLines(detail).strip();
            int newline = trimmed.indexOf('\n');
            String firstLine = newline > 0 ? trimmed.substring(0, newline).strip() : trimmed;
            if (!ScoreCategoryClassifier.needsSpecificTitle(firstLine)) {
                return SCORE_PREDICATE.matcher(stripTrailingScore(firstLine)).find() ? ""
                        : firstLine.substring(0, Math.min(firstLine.length(), LEGACY_FALLBACK_LENGTH));
            }
        }
        return limitedDimension;
    }

    /**
     * 清理表头/维度单元格中的分值括注、换行截断及前导序号或大类前缀（如 "商务评分（20" → "商务评分"，"3 | 营业总收入" → "营业总收入"）。
     */
    public static String cleanDimensionLabel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        String text = raw.replace('\u00A0', ' ').trim();
        text = SPLIT_SCORE_NEWLINE.matcher(text).replaceAll("$1$2");
        if (text.contains("|")) {
            String[] parts = text.split("\\s*\\|\\s*");
            String selected = "";
            for (String part : parts) {
                String cleanedPart = stripScoreAndNumbering(part);
                if (isNonTitleCell(cleanedPart)) {
                    continue;
                }
                if (!ScoreCategoryClassifier.needsSpecificTitle(cleanedPart)) {
                    selected = cleanedPart;
                    break;
                }
                if (selected.isEmpty()) {
                    selected = cleanedPart;
                }
            }
            if (!selected.isEmpty()) {
                text = selected;
            }
        }
        return UNCLOSED_SCORE_SUFFIX.matcher(stripTrailingScore(text)).replaceFirst("").replaceAll("\\s+", " ").trim();
    }

    /**
     * 解析展示和落库用的评分维度名称。大类、残缺值和旧版复制的计分正文优先从原文恢复标题。
     */
    public static String resolveDimension(String currentDimension, String detail) {
        boolean unclosed = currentDimension != null && ScoreCategoryClassifier.hasUnclosedBracket(currentDimension);
        boolean copiedRuleBody = isLegacyRuleBodyName(currentDimension, detail);
        String cleaned = cleanDimensionLabel(currentDimension);
        if (!unclosed && !copiedRuleBody && !ScoreCategoryClassifier.needsSpecificTitle(cleaned)) {
            return cleaned;
        }
        Optional<String> extracted = extract(detail);
        if (extracted.isPresent()) {
            return extracted.get();
        }
        if (unclosed || copiedRuleBody || ScoreCategoryClassifier.isInvalidOrPlaceholderName(cleaned)) {
            return "";
        }
        return cleaned;
    }

    private static boolean isLegacyRuleBodyName(String dimension, String detail) {
        if (dimension == null || dimension.isBlank() || detail == null || detail.isBlank()) {
            return false;
        }
        String name = dimension.trim();
        String normalized = normalizeRuleLines(detail);
        if (matchesLegacyRuleBody(name, normalized)) {
            return true;
        }
        // Structured candidates kept the complete source row and copied only the detail cell into the name.
        if (normalized.contains("|")) {
            for (String cell : normalized.split("\\|")) {
                if (matchesLegacyRuleBody(name, cell)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesLegacyRuleBody(String name, String normalizedText) {
        String firstLine = normalizedText.trim().split("\\n", 2)[0].trim();
        // The legacy mapper copied up to 50 characters, which could omit the scoring predicate.
        String legacyName = firstLine.substring(0, Math.min(firstLine.length(), LEGACY_FALLBACK_LENGTH)).trim();
        return name.equals(legacyName) && SCORE_PREDICATE.matcher(stripTrailingScore(firstLine)).find();
    }

    /**
     * Returns the title before the first colon or from a heading-style first line.
     * The matched prefix must be literal text from the original text.
     */
    public static Optional<String> extract(String detail) {
        if (detail == null || detail.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeRuleLines(detail);
        if (normalized.isBlank()) {
            return Optional.empty();
        }
        normalized = SPLIT_SCORE_NEWLINE.matcher(normalized).replaceAll("$1$2");
        String[] lines = normalized.stripTrailing().split("\n");
        int firstLineIndex = 0;
        while (firstLineIndex < lines.length) {
            String line = lines[firstLineIndex].trim();
            boolean hasNextLine = firstLineIndex + 1 < lines.length;
            if (line.contains("|")) {
                Optional<String> piped = extractFromPipeLine(line, hasNextLine);
                if (piped.isPresent()) {
                    return piped;
                }
            }
            String probe = stripTrailingScore(line);
            if (!hasNextLine || (!PURE_WEIGHT_CELL.matcher(probe).matches() && !ScoreCategoryClassifier.needsSpecificTitle(probe))) {
                break;
            }
            firstLineIndex++;
        }
        String firstLine = lines[firstLineIndex].trim();
        int nextLineIndex = firstLineIndex + 1;
        boolean hasFollowingLines = nextLineIndex < lines.length;

        Matcher matcher = COLON.matcher(firstLine);
        if (matcher.matches()) {
            String contentAfterColon = matcher.group(2).trim();
            if (contentAfterColon.isEmpty()) {
                for (int i = nextLineIndex; i < lines.length; i++) {
                    if (!lines[i].isBlank()) {
                        contentAfterColon = lines[i].trim();
                        break;
                    }
                }
            }
            if (!contentAfterColon.isEmpty()) {
                String title = stripScoreAndNumbering(matcher.group(1));
                if (isAcceptableTitle(title, false)) {
                    return Optional.of(title);
                }
            }
        }

        // When there is no colon, but there is body content following line 1:
        if (hasFollowingLines) {
            String candidate = stripScoreAndNumbering(firstLine);

            // If line 1 was wrapped across lines due to table column width (e.g. "能够提供最长" + "账期")
            if (!candidate.isEmpty() && candidate.length() <= 15
                    && !PUNCTUATED_SENTENCE.matcher(candidate).find()
                    && !SCORE_PREDICATE.matcher(candidate).find()
                    && nextLineIndex + 1 < lines.length) {
                String secondLine = stripTrailingScore(lines[nextLineIndex]);
                if (!secondLine.isEmpty() && secondLine.length() <= 6
                        && !PUNCTUATED_SENTENCE.matcher(secondLine).find()
                        && !SCORE_PREDICATE.matcher(secondLine).find()) {
                    candidate = candidate + secondLine;
                }
            }

            if (isAcceptableTitle(candidate, true)) {
                for (int i = nextLineIndex; i < lines.length; i++) {
                    String line = lines[i].trim();
                    if (!line.isEmpty() && !line.equals(candidate)) {
                        return Optional.of(candidate);
                    }
                }
            }
        }

        return Optional.empty();
    }

    private static Optional<String> extractFromPipeLine(String firstLine, boolean hasFollowingLines) {
        String[] cells = firstLine.split("\\s*\\|\\s*");
        for (int i = 0; i < cells.length; i++) {
            String rawCell = cells[i].trim();
            if (isNonTitleCell(rawCell)) {
                continue;
            }
            Matcher colonMatcher = COLON.matcher(rawCell);
            if (colonMatcher.matches() && (hasFollowingLines || !colonMatcher.group(2).isBlank())) {
                String title = stripScoreAndNumbering(colonMatcher.group(1));
                if (isAcceptableTitle(title, false)) {
                    return Optional.of(title);
                }
            }
            String cleaned = stripScoreAndNumbering(rawCell);
            if (!ScoreCategoryClassifier.needsSpecificTitle(cleaned)) {
                boolean hasBody = hasFollowingLines || i + 1 < cells.length;
                return hasBody && isAcceptableTitle(cleaned, true) ? Optional.of(cleaned) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static boolean isNonTitleCell(String text) {
        return text.isEmpty() || PURE_WEIGHT_CELL.matcher(text).matches()
                || ScoreCategoryClassifier.isAlphanumericCode(text);
    }

    private static String normalizeRuleLines(String text) {
        return HTML_BREAK.matcher(text).replaceAll("\n").replace('\u00A0', ' ');
    }

    private static String stripScoreAndNumbering(String text) {
        String withoutScore = stripTrailingScore(text);
        return NUMBER_PREFIX.matcher(withoutScore).replaceFirst("").trim();
    }

    private static boolean isAcceptableTitle(String title, boolean checkVerbAndPredicate) {
        if (title.length() > MAX_TITLE_LENGTH
                || PUNCTUATED_SENTENCE.matcher(title).find()
                || PURE_WEIGHT_CELL.matcher(title).matches()
                || ScoreCategoryClassifier.needsSpecificTitle(title)) {
            return false;
        }
        return !checkVerbAndPredicate
                || (!RULE_VERB_PREFIX.matcher(title).matches() && !SCORE_PREDICATE.matcher(title).find());
    }
}
