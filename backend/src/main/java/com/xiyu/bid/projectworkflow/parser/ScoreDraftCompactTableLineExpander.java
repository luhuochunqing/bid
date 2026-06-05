package com.xiyu.bid.projectworkflow.parser;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ScoreDraftCompactTableLineExpander {

    private static final String SCORE_CELL = "(?:最高|满分)?\\d+(?:\\.\\d+)?分?";
    private static final Pattern COMPACT_SERIAL_ROW = Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s+(.+)$");
    private static final Pattern COMPACT_TECHNICAL_ROW = Pattern.compile("^(.+?)\\s+(" + SCORE_CELL + ")\\s+(.+)$");
    private static final Pattern TRAILING_SCORE_CELL = Pattern.compile("^(.*)\\s+(" + SCORE_CELL + ")$");
    private static final List<String> RULE_STARTERS = List.of(
            "供应商", "根据", "按", "每提供", "提供", "最大程度", "接受平台", "结算周期",
            "办公用品", "方案应包含", "1、", "（一）", "若该商品"
    );
    private static final List<String> TITLE_REJECT_PREFIXES = List.of(
            "根据", "每提供", "提供", "最大程度", "接受平台", "办公用品", "方案应包含",
            "1、", "（一）", "若该商品"
    );

    private ScoreDraftCompactTableLineExpander() {
    }

    static List<String> expand(String line) {
        List<String> serialRow = splitCompactSerialRow(line);
        if (!serialRow.isEmpty()) {
            return serialRow;
        }
        List<String> technicalRow = splitCompactTechnicalRow(line);
        if (!technicalRow.isEmpty()) {
            return technicalRow;
        }
        return List.of(line);
    }

    static String cleanTitle(String raw) {
        String title = Optional.ofNullable(raw).orElse("").trim();
        if (title.isBlank()) {
            return "";
        }

        Matcher combined = Pattern.compile("^(\\d+)\\s*(.+)$").matcher(title);
        if (combined.matches()) {
            title = combined.group(2).trim();
        }

        for (String starter : RULE_STARTERS) {
            int index = title.indexOf(starter);
            if (index > 0) {
                return title.substring(0, index).trim();
            }
        }
        return title;
    }

    static boolean isPotentialTitle(String line) {
        if (line == null) {
            return false;
        }
        String text = line.trim();
        if (text.isBlank()
                || ScoreDraftLineClassifier.isTableHeader(text)
                || ScoreDraftLineClassifier.isScoreOnlyLine(text)) {
            return false;
        }
        if (text.length() > 80 || text.contains("http")) {
            return false;
        }
        if (text.contains("，") || text.contains("。") || text.contains("；") || text.contains("：") || text.contains("得")) {
            return false;
        }
        return TITLE_REJECT_PREFIXES.stream().noneMatch(text::startsWith);
    }

    private static List<String> splitCompactSerialRow(String line) {
        Matcher matcher = COMPACT_SERIAL_ROW.matcher(line);
        if (!matcher.matches()) {
            return List.of();
        }
        String serial = matcher.group(1).trim();
        String rest = matcher.group(2).trim();
        int ruleStart = findRuleStart(rest);
        if (ruleStart <= 0) {
            return List.of();
        }

        String titleAndMaybeScore = rest.substring(0, ruleStart).trim();
        String ruleAndMaybeScore = rest.substring(ruleStart).trim();
        Matcher scoreBeforeRule = TRAILING_SCORE_CELL.matcher(titleAndMaybeScore);
        if (scoreBeforeRule.matches() && isPotentialTitle(scoreBeforeRule.group(1).trim())) {
            return List.of(serial, scoreBeforeRule.group(1).trim(), scoreBeforeRule.group(2).trim(), ruleAndMaybeScore);
        }
        if (!isPotentialTitle(titleAndMaybeScore)) {
            return List.of();
        }

        Matcher scoreAfterRule = TRAILING_SCORE_CELL.matcher(ruleAndMaybeScore);
        if (scoreAfterRule.matches()) {
            return List.of(serial, titleAndMaybeScore, scoreAfterRule.group(1).trim(), scoreAfterRule.group(2).trim());
        }
        return List.of(serial, titleAndMaybeScore, ruleAndMaybeScore);
    }

    private static List<String> splitCompactTechnicalRow(String line) {
        Matcher matcher = COMPACT_TECHNICAL_ROW.matcher(line);
        if (!matcher.matches()) {
            return List.of();
        }
        String title = matcher.group(1).trim();
        String rule = matcher.group(3).trim();
        if (!isPotentialTitle(title) || !startsLikeRule(rule)) {
            return List.of();
        }
        return List.of(title, matcher.group(2).trim(), rule);
    }

    private static int findRuleStart(String line) {
        int first = -1;
        for (String starter : RULE_STARTERS) {
            int index = line.indexOf(starter);
            if (index > 0 && (first < 0 || index < first)) {
                first = index;
            }
        }
        return first;
    }

    private static boolean startsLikeRule(String line) {
        return RULE_STARTERS.stream().anyMatch(line::startsWith)
                || line.contains("。")
                || line.contains("得");
    }
}
