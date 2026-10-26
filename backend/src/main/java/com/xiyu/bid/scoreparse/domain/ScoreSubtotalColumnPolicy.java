package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Selects the source maximum column; never treats the largest number as column evidence.
 * A 满分/总分 mention is table-total evidence only when it states the whole-table total:
 * a per-item statement (single-item quantifier/subject left of the match in the same cell)
 * is never table-total evidence; when in doubt the column stays unresolved. */
public final class ScoreSubtotalColumnPolicy {
    static final Pattern NUMBER = Pattern.compile("^\\s*(\\d+(?:\\.\\d+)?)\\s*分?\\s*$");
    static final Pattern EXPLICIT_TOTAL = Pattern.compile("(?<!(?:单项|每项|每条|每处|每篇|每个|本项|此项))(?:总分|满分)\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    /** 单项分值语义：同一分句内匹配点左侧的单项量词/主体。该数字是单项分值而非整表总量。
     * 覆盖"一"插入变体（每一项/每一条）；"本项"后跟"目"时是"本项目"（整表主体），用 (?!目) 排除。
     * 汇总语义：最后一个单项词与总分关键词之间出现汇总词（之和/合计/共计/累计/汇总/总和/总计）时，
     * 该数字是「各单项之合计」——整表总量，不得判为单项（如"所有单项合计总分100分"）。 */
    private static final Pattern SINGLE_ITEM_CONTEXT =
            Pattern.compile("单项|每一?项|每一?条|每一?处|每一?篇|每一?个|本项(?!目)|此项");
    private static final Pattern PER_ITEM_AGGREGATION =
            Pattern.compile("之和|合计|共计|累计|汇总|总和|总计");
    private static final Pattern MAXIMUM = Pattern.compile("^(?:最高分(?:值|数)?|最高得分|满分)(?:[（(\\[【]分[）)\\]】])?$");
    private static final Pattern SCORE = Pattern.compile("^(?:分值|分数|得分|评分分值)(?:[（(\\[【]分[）)\\]】])?$");
    private static final Pattern EXPLICIT_SUMMARY_MAXIMUM = Pattern.compile(
            "(?<!(?:单项|每项|每条|每处|每篇|每个|本项|此项))满分\\s*[：:（(为]?\\s*(\\d+(?:\\.\\d+)?)\\s*分");
    private ScoreSubtotalColumnPolicy() {}

    public record Selection(int column, boolean unresolved) {
        public OriginalScoreEvidencePolicy.Problem problem(String tableId) {
            return new OriginalScoreEvidencePolicy.Problem("", "subtotal", "TABLE_SUBTOTAL_COLUMN_UNRESOLVED",
                    "one source maximum column", null, tableId);
        }
    }

    public static Selection select(CanonicalTable table, int headerRows, List<CanonicalCellDto> summary) {
        var headers = table.cells().stream().filter(cell -> cell.row() != null && cell.row() == headerRows - 1
                && !Boolean.TRUE.equals(cell.isMergedContinue())).toList();
        int column = select(headers, summary);
        boolean verified = column >= 0 && summary.stream().anyMatch(cell -> java.util.Objects.equals(cell.column(), column)
                && hasValue(cell.rawText()));
        return new Selection(column, !verified);
    }

    private static int select(List<CanonicalCellDto> headers, List<CanonicalCellDto> summary) {
        Set<Integer> maximum = columns(headers, MAXIMUM);
        if (!maximum.isEmpty()) return only(maximum);
        Set<Integer> score = columns(headers, SCORE);
        if (!score.isEmpty()) return only(score);
        Set<Integer> explicitMaximum = valueColumns(summary, EXPLICIT_SUMMARY_MAXIMUM);
        Set<Integer> explicitTotals = valueColumns(summary, EXPLICIT_TOTAL);
        if (explicitMaximum.size() == 1 && explicitTotals.equals(explicitMaximum)) {
            int candidateCol = explicitMaximum.iterator().next();
            // 检查该列（或同一单元格）是否包含相互冲突的显式总分/满分声明
            if (hasConflictingTotals(summary, candidateCol)) {
                return -1;
            }
            return candidateCol;
        }
        // Legacy tables without a detected header may carry one unambiguous summary value.
        // A detected but unknown header is not score-column evidence.
        // Per-item mentions cannot be legacy evidence either (same grammar as the main path).
        if (!headers.isEmpty()) return -1;
        Set<Integer> numeric = summary.stream().filter(c -> c.column() != null && c.column() > 0
                        && hasLegacyValue(c.rawText()))
                .map(CanonicalCellDto::column).collect(Collectors.toSet());
        return only(numeric);
    }

    private static boolean hasLegacyValue(String text) {
        if (text == null) return false;
        if (NUMBER.matcher(text).matches()) return true;
        return EXPLICIT_TOTAL.matcher(text).find() && hasTableTotalEvidence(text, EXPLICIT_TOTAL);
    }

    private static boolean hasConflictingTotals(List<CanonicalCellDto> summary, int column) {
        Set<String> distinctValues = new java.util.HashSet<>();
        for (var cell : summary) {
            if (cell.column() != null && cell.column() == column && cell.rawText() != null) {
                var matcher = EXPLICIT_TOTAL.matcher(cell.rawText());
                while (matcher.find()) {
                    // 与证据判定同一文法：单项分值提及不计入冲突（方向为 fail-closed，见 hasTableTotalEvidence）
                    if (isSingleItemMention(cell.rawText(), matcher.start())) {
                        continue;
                    }
                    distinctValues.add(new java.math.BigDecimal(matcher.group(1)).stripTrailingZeros().toPlainString());
                }
            }
        }
        return distinctValues.size() > 1;
    }

    private static Set<Integer> columns(List<CanonicalCellDto> headers, Pattern pattern) {
        return headers.stream().filter(c -> c.column() != null && pattern.matcher(normalize(c.rawText())).matches())
                .map(CanonicalCellDto::column).collect(Collectors.toSet());
    }

    private static Set<Integer> valueColumns(List<CanonicalCellDto> cells, Pattern pattern) {
        return cells.stream().filter(c -> c.column() != null
                        && hasTableTotalEvidence(c.rawText() == null ? "" : c.rawText(), pattern))
                .map(CanonicalCellDto::column).collect(Collectors.toSet());
    }

    /**
     * 整表总量证据判定（语义规则，而非关键词紧邻排除）：
     * 同一单元格内至少存在一个 满分/总分 匹配、且该匹配所在分句（按 ，。；; 与换行划分）
     * 左侧没有单项量词/主体时，才算整表满分声明。"本表单项 满分10分"、"每项最高满分10分"、
     * "每一项满分10分"等单项分值说明一律不算；紧邻排除由正则后行断言承担，本规则覆盖
     * 空格/助词隔开的情形。取舍声明：列识别仍沿用既有契约（只有满分声明能确定小计列，
     * 总分声明不能）——"每项 满分10分，满分100分"中干净的满分100分独立成证、决议 100；
     * "每项 满分10分，总分100分"因缺乏满分列证据保持未决（fail-closed，不猜测），
     * 其整表总量仍由正文总分正常决议。hasConflictingTotals 复用同一文法，不存在双重标准。
     */
    private static boolean hasTableTotalEvidence(String rawText, Pattern pattern) {
        var matcher = pattern.matcher(rawText);
        while (matcher.find()) {
            if (!isSingleItemMention(rawText, matcher.start())) {
                return true;
            }
        }
        return false;
    }

    /** 匹配点所在分句的左侧是否出现单项量词/主体（分句按 ，。；; 与换行划分；顿号不断句，取保守方向）。
     * 包可见：OriginalScoringContext 的表格/正文/兜底三条解析路径复用同一文法，杜绝双重标准。 */
    static boolean isSingleItemMention(String rawText, int matchStart) {
        int clauseStart = 0;
        for (int i = matchStart - 1; i >= 0; i--) {
            char ch = rawText.charAt(i);
            if (ch == '，' || ch == '。' || ch == '；' || ch == ';' || ch == '\n') {
                clauseStart = i + 1;
                break;
            }
        }
        String left = rawText.substring(clauseStart, matchStart);
        var matcher = SINGLE_ITEM_CONTEXT.matcher(left);
        int lastEnd = -1;
        while (matcher.find()) lastEnd = matcher.end();
        if (lastEnd < 0) return false;
        // 最后一个单项词与总分关键词之间出现汇总词 → 「各单项之合计」是整表总量，不是单项分值
        return !PER_ITEM_AGGREGATION.matcher(left.substring(lastEnd)).find();
    }

    private static boolean hasValue(String text) {
        if (text == null) return false;
        if (NUMBER.matcher(text).matches()) return true;
        return hasTableTotalEvidence(text, EXPLICIT_TOTAL);
    }

    private static int only(Set<Integer> columns) { return columns.size() == 1 ? columns.iterator().next() : -1; }
    private static String normalize(String text) { return text == null ? "" : text.replaceAll("[\\s*]+", ""); }

}
