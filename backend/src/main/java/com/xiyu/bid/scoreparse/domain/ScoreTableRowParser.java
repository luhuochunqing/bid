// Input: 管道表格数据行原文（来自 ScoreTableBlockLocator）
// Output: Optional<ScoreCandidate>（一行 = 一候选；不可解析返回 empty）
// Pos: scoreparse/domain — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 055 FR-5 / research R5 列对齐策略

package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 评分表格行解析（spec 055 召回五）。
 * <p>列对齐策略（research R5）：
 * <ul>
 *   <li>code：首列匹配短编号模式（1 / 2.2.1 / A1）</li>
 *   <li>weight：从右向左首个纯分值短列（"5" / "5分"）；无则取行内"得X分"档位最大值（满分语义）</li>
 *   <li>detail：非编号非分值列中最长文本列；无可识别列（合并单元格/单列）→ 整行原文降级</li>
 *   <li>dim：剩余列中最长中文短列（评分项名称列，如"不平衡报价评审"）；无则 null</li>
 * </ul>
 */
public final class ScoreTableRowParser {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z]{0,2}\\d+(\\.\\d+)*$");
    private static final Pattern PURE_WEIGHT_COLUMN = Pattern.compile("^[~≈]?\\d{1,3}(\\.\\d+)?\\s*分?$");
    private static final Pattern GRADE_SCORE = Pattern.compile("得\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分");
    private static final Pattern CONTAINS_HAN = Pattern.compile("[\\p{IsHan}]");
    private static final int DIM_MAX_CHARS = 20;

    private ScoreTableRowParser() {
    }

    /** 解析单行；null/空白/分隔行/全空列返回 empty */
    public static Optional<ScoreCandidate> parse(String row) {
        if (row == null || row.isBlank() || isSeparatorLike(row)) {
            return Optional.empty();
        }
        List<String> columns = splitColumns(row);
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        int codeIndex = codeColumnIndex(columns);
        String code = codeIndex >= 0 ? columns.get(codeIndex) : "";
        int weightIndex = pureWeightIndex(columns);
        BigDecimal weight = weightIndex >= 0
                ? new BigDecimal(columns.get(weightIndex).replaceAll("[^0-9.]", ""))
                : maxGradeScore(row);
        int detailIndex = detailColumnIndex(columns, codeIndex, weightIndex);
        String detail = detailIndex >= 0 ? columns.get(detailIndex) : wholeRowFallback(row);
        if (detail.isBlank()) {
            return Optional.empty();
        }
        String dim = extractDim(columns, codeIndex, weightIndex, detailIndex);
        return Optional.of(new ScoreCandidate(code, dim, detail, weight, null, null,
                row.trim(), null, "TABLE_ROW"));
    }

    private static boolean isSeparatorLike(String row) {
        return row.replace("|", "").replace("-", "").replace(":", "")
                .replace(" ", "").isEmpty();
    }

    private static List<String> splitColumns(String row) {
        List<String> columns = new ArrayList<>();
        for (String part : row.split("\\|", -1)) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                columns.add(trimmed);
            }
        }
        return columns;
    }

    private static int codeColumnIndex(List<String> columns) {
        String first = columns.get(0);
        return first.length() <= 8 && CODE_PATTERN.matcher(first).matches() ? 0 : -1;
    }

    private static int pureWeightIndex(List<String> columns) {
        for (int i = columns.size() - 1; i >= 0; i--) {
            String column = columns.get(i);
            if (column.length() <= 10 && PURE_WEIGHT_COLUMN.matcher(column).matches()) {
                return i;
            }
        }
        return -1;
    }

    /** 行内"得X分"档位最大值（满分语义："优秀得4分，良好得2-3分" → 4）；无匹配返回 null */
    private static BigDecimal maxGradeScore(String row) {
        Matcher matcher = GRADE_SCORE.matcher(row);
        BigDecimal max = null;
        while (matcher.find()) {
            BigDecimal value = new BigDecimal(matcher.group(1));
            max = max == null || value.compareTo(max) > 0 ? value : max;
        }
        return max;
    }

    /** detail 列：非编号非分值列中最长文本列；不存在返回 -1 */
    private static int detailColumnIndex(List<String> columns, int codeIndex, int weightIndex) {
        int detailIndex = -1;
        int bestLength = 0;
        for (int i = 0; i < columns.size(); i++) {
            if (i == codeIndex || i == weightIndex) {
                continue;
            }
            if (columns.get(i).length() > bestLength) {
                bestLength = columns.get(i).length();
                detailIndex = i;
            }
        }
        return detailIndex;
    }

    /** dim：剩余列中最长中文短列（评分项名称列）；无中文列返回 null */
    private static String extractDim(List<String> columns, int codeIndex, int weightIndex, int detailIndex) {
        String best = null;
        for (int i = 0; i < columns.size(); i++) {
            if (i == codeIndex || i == weightIndex || i == detailIndex) {
                continue;
            }
            String column = columns.get(i);
            if (column.length() <= DIM_MAX_CHARS && CONTAINS_HAN.matcher(column).find()
                    && (best == null || column.length() > best.length())) {
                best = column;
            }
        }
        return best;
    }

    /** 合并单元格/单列等列语义失效场景：整行原文降级（不丢行、不编造列语义） */
    private static String wholeRowFallback(String row) {
        return row.replace("|", " ").trim();
    }
}
