package com.xiyu.bid.projectworkflow.parser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScoreDraftLineClassifier {

    private static final Pattern SECTION_HEADING = Pattern.compile(
            "^(?:(?:第[一二三四五六七八九十]+章)?\\s*)?(?:\\d+(?:\\.\\d+)*)?\\s*.*?(商务|技术|价格)评分标准(?:表|如下|说明)?(?:（[^）]+）)?$"
    );
    private static final Pattern DIGITS_ONLY = Pattern.compile("^\\d+(?:\\.\\d+)?$");
    private static final Set<String> TABLE_HEADERS = Set.of(
            "序号", "评价项目", "评分标准", "评标分值", "评审内容", "评分因素", "分值", "评分细则",
            "评分标准说明", "评分项目", "分数", "评分因素及标准", "条款号", "详细", "评审", "标准"
    );

    private ScoreDraftLineClassifier() {
    }

    static List<String> normalizeLines(String extractedText) {
        return Arrays.stream(normalizeExtractedText(extractedText).split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .flatMap(line -> ScoreDraftCompactTableLineExpander.expand(line).stream())
                .toList();
    }

    static Map<String, List<String>> splitSections(List<String> lines) {
        Map<String, Integer> starts = new LinkedHashMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.contains("详见第三章") || line.contains("详见评审程序及办法")) {
                continue;
            }
            Matcher matcher = SECTION_HEADING.matcher(line);
            if (matcher.matches()) {
                String category = switch (matcher.group(1)) {
                    case "商务" -> "business";
                    case "技术" -> "technical";
                    case "价格" -> "price";
                    default -> null;
                };
                if (category != null && !starts.containsKey(category)) {
                    starts.put(category, index);
                }
            }
        }

        Map<String, List<String>> sections = new LinkedHashMap<>();
        List<Map.Entry<String, Integer>> entries = new ArrayList<>(starts.entrySet());
        for (int index = 0; index < entries.size(); index++) {
            int start = entries.get(index).getValue() + 1;
            int end = index + 1 < entries.size() ? entries.get(index + 1).getValue() : lines.size();
            sections.put(entries.get(index).getKey(), lines.subList(start, end));
        }
        return sections;
    }

    static String normalizeLineForParsing(String line) {
        return Optional.ofNullable(line)
                .orElse("")
                .replaceAll("^第[一二三四五六七八九十]+章.*$", "")
                .replaceAll("^条款号$", "")
                .replaceAll("^详细$", "")
                .replaceAll("^评审$", "")
                .replaceAll("^标准$", "")
                .trim();
    }

    static boolean isTableHeader(String line) {
        return TABLE_HEADERS.contains(Optional.ofNullable(line).orElse(""));
    }

    static boolean looksLikeRowStart(List<String> lines, int index) {
        if (!isSerialLine(lines.get(index)) || index + 1 >= lines.size()) {
            return false;
        }
        return ScoreDraftCompactTableLineExpander.isPotentialTitle(lines.get(index + 1));
    }

    static boolean isScoreOnlyLine(String line) {
        return DIGITS_ONLY.matcher(line).matches() || line.matches("^(?:最高|满分)?\\s*\\d+(?:\\.\\d+)?分$");
    }

    static String cleanTitle(String raw) {
        return ScoreDraftCompactTableLineExpander.cleanTitle(raw);
    }

    static String joinLines(List<String> lines) {
        return lines.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    static List<String> splitNumberedClauses(String text) {
        String normalized = Optional.ofNullable(text).orElse("")
                .replaceAll("(?<!^)\\s*(?=(?:\\d+、|（[一二三四五六七八九十]+）))", "\n");
        return Arrays.stream(normalized.split("\n"))
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .toList();
    }

    private static String normalizeExtractedText(String text) {
        return Optional.ofNullable(text)
                .orElse("")
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace('\u0007', '\n')
                .replace('\u000b', '\n')
                .replace('\u000c', '\n')
                .replace('\u00A0', ' ')
                .replaceAll("[\\t]+", "\n")
                .replaceAll(" +", " ")
                .replaceAll("\\n{2,}", "\n");
    }

    private static boolean isSerialLine(String line) {
        return DIGITS_ONLY.matcher(line).matches();
    }
}
