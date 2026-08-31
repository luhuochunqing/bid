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
 * <p>列对齐策略（research R5，2026-08-28 优化）：
 * <ul>
 *   <li>code：首列匹配短编号模式（1 / 2.2.1 / A1）</li>
 *   <li>weight：优先从第3列提取括号分值（"财务状况（10分）" → 10）；
 *       次选从右向左首个纯分值短列（"5" / "5分"）；
 *       兜底取行内"得X分"档位最大值（满分语义）</li>
 *   <li>detail：5列表格时取列4+列5拼接（评分要求+评分细则）；
 *       否则非编号非分值列中最长文本列；无可识别列（合并单元格/单列）→ 整行原文降级</li>
 *   <li>dim：列3去掉括号分值后的文本；若列3无括号分值则取剩余列中最长中文短列</li>
 * </ul>
 */
public final class ScoreTableRowParser {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Za-z]{0,2}\\d+([\\.\\(]\\d+[\\)]?)*$");
    private static final Pattern PURE_WEIGHT_COLUMN = Pattern.compile("^[~≈]?\\d{1,3}(\\.\\d+)?\\s*(?:分|%)?$");
    private static final Pattern GRADE_SCORE = Pattern.compile("得\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分");
    private static final Pattern BRACKET_WEIGHT = ScoreParseConstants.BRACKET_WEIGHT_PATTERN;
    private static final Pattern CONTAINS_HAN = Pattern.compile("[\\p{IsHan}]");
    private static final Pattern SCORE_SEMANTIC = Pattern.compile("评分|评审|评标|分值|得分|打分|加分|扣分|评审标准|评分标准");
    private static final Pattern NON_SCORE_PROCEDURE_PATTERN = ScoreParseConstants.NON_SCORE_PROCEDURE_PATTERN;
    private static final Pattern SUMMARY_ROW_PATTERN = Pattern.compile(
            "分值构成|总分构成|分值分布|总分100分"
    );
    private static final Pattern CATEGORY_SUMMARY_ROW_PATTERN = Pattern.compile(
            "^[^。\n;；]{1,35}?[：:（(]\\s*\\d+(?:\\.\\d+)?\\s*(?:分|%|分值)\\s*[）)]?$"
    );
    private static final int DIM_MAX_CHARS = 20;

    private ScoreTableRowParser() {
    }
    public static Optional<ScoreCandidate> parse(String row) {
        if (row == null || row.isBlank() || isSeparatorLike(row)) {
            return Optional.empty();
        }
        List<String> columns = splitColumns(row);
        if (columns.isEmpty()) {
            return Optional.empty();
        }
        // 孤立纯数字/超短单元格（如 "| 10 |"）非评分条款，防假候选穿透守卫（评审 m5）
        if (columns.size() == 1 && columns.get(0).length() <= 4) {
            return Optional.empty();
        }
        
        // 汇总说明行（如"3.1 分值构成（总分100分）"）非可打分单项，过滤排除
        if (SUMMARY_ROW_PATTERN.matcher(row).find() && !GRADE_SCORE.matcher(row).find()) {
            return Optional.empty();
        }

        // 纯大类总览行（如"| 1 | 详细评审 | 公司财务状况：10分 |"）无具体细则，过滤排除防父大项占坑
        if (columns.size() <= 3) {
            String lastCol = columns.get(columns.size() - 1).trim();
            if (CATEGORY_SUMMARY_ROW_PATTERN.matcher(lastCol).find() && !GRADE_SCORE.matcher(row).find()) {
                return Optional.empty();
            }
        }

        // 前附表程序性非评分行与合同履约考核表过滤
        boolean isProcedural = NON_SCORE_PROCEDURE_PATTERN.matcher(row).find();
        boolean isContractAssessment = ScoreParseConstants.CONTRACT_ASSESSMENT_PATTERN.matcher(row).find()
                && !row.contains("评分标准") && !row.contains("评审标准");
        boolean hasExplicitGradeScore = GRADE_SCORE.matcher(row).find();
        boolean hasBracketWeight = BRACKET_WEIGHT.matcher(row).find();
        if ((isProcedural || isContractAssessment) && !hasExplicitGradeScore && !hasBracketWeight) {
            return Optional.empty();
        }
        
        // 提取叶子编号（多层级表格时优先取最右侧的子编号，如 1.1 优于 1）
        int codeIndex = leafCodeColumnIndex(columns);
        String code = codeIndex >= 0 ? columns.get(codeIndex) : "";
        
        // 提取括号分值（如"财务状况（10分）"）
        BracketWeightResult bracketResult = extractBracketWeight(columns);
        
        // 提取 detail（5列表格取列4+列5拼接，其他取最右侧有效文本列）
        String detail = extractDetail(columns, codeIndex, bracketResult.col3Index());
        if (detail.isBlank()) {
            return Optional.empty();
        }
        
        // 分值提取优先级（bracket 优先于纯分值列，PR !2481 dd6492143 引入）：
        // 括号分值是维度名上的显式满分标注（"财务状况（10分）"→10），语义最强；纯分值列次之
        // （叶子项满分）——两者并存且不一致时以括号标注为准；再退 detail"得X分"档位最大值。
        // 锚定：parse_bracketWeightTakesPrecedenceOverPureWeightColumn + 金标 testProject21ScoreParsing
        int weightIndex = pureWeightIndex(columns, codeIndex);
        BigDecimal weight = null;
        if (bracketResult.weight() != null) {
            weight = bracketResult.weight();
        } else if (weightIndex >= 0) {
            String val = columns.get(weightIndex).replaceAll("[^0-9.]", "");
            if (!val.isEmpty()) {
                try {
                    weight = new BigDecimal(val);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (weight == null) {
            BigDecimal detailGradeScore = maxGradeScore(detail);
            if (detailGradeScore != null) {
                weight = detailGradeScore;
            } else {
                weight = maxGradeScore(row);
            }
        }
        
        // 提取 dim 分类名称
        String dim = bracketResult.dimFromCol3();
        if (dim == null || dim.isBlank()) {
            int detailIndex = detailColumnIndex(columns, codeIndex, weightIndex);
            dim = extractDim(columns, codeIndex, weightIndex, detailIndex);
        }

        // 若 weight 为空且行内完全无评分语义，不应作为评分候选入池
        if (weight == null && !SCORE_SEMANTIC.matcher(row).find()) {
            return Optional.empty();
        }
        
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

    /** 提取叶子编号列索引：多列时排查最右侧纯分值列，优先取更具体的子编号列（如 1.1 优于 1） */
    private static int leafCodeColumnIndex(List<String> columns) {
        if (columns.isEmpty()) {
            return -1;
        }
        if (columns.size() <= 2) {
            String first = columns.get(0);
            return first.length() <= 8 && CODE_PATTERN.matcher(first).matches() ? 0 : -1;
        }
        // 表格列数 >= 3 时，最后一列通常是细则或分值列，编号列仅出现在前 columns.size() - 1 列中
        int bestIdx = -1;
        int maxScan = Math.min(columns.size() - 1, 3);
        for (int i = 0; i < maxScan; i++) {
            String col = columns.get(i);
            if (col.length() <= 8 && CODE_PATTERN.matcher(col).matches()) {
                bestIdx = i;
            }
        }
        return bestIdx;
    }

    /** 纯分值列索引：从右向左扫描（至少在列 1 及以后，列 0 是编号或大项名），排除编号列 */
    private static int pureWeightIndex(List<String> columns, int codeIndex) {
        for (int i = columns.size() - 1; i >= 1; i--) {
            if (i == codeIndex) {
                continue;
            }
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

    /** dim：剩余列中最长中文短列（评分项名称列）；自动清洗末尾的分值后缀（如"公司财务状况：10分" → "公司财务状况"） */
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
        if (best != null) {
            // 清洗大类名称中的分值标识（如"公司财务状况：10分"、"商务标（56分）"）
            best = best.replaceAll("[：:（(]\\s*\\d+(?:\\.\\d+)?\\s*(?:分|%|分值)\\s*[）)]?", "").trim();
        }
        return (best == null || best.isBlank()) ? null : best;
    }

    /**
     * 提取括号分值（主项分值）。扫描列 3 → 列 0；多列表（>=3 列）跳过列 0——序号列误含
     * 括号（如"1（10分）"）会冒充分值；2 列及以下窄表列 0 常是维度名本身（如
     * "| 供应商业绩（7分） | 细则 |"），必须参与，否则退化为"得X分"档位最大值冒充满分
     * （复审 jshell 实证：weight 7 → 2 错值）。
     *
     * @return BracketWeightResult(weight, dimFromCol3, col3Index)
     */
    private static BracketWeightResult extractBracketWeight(List<String> columns) {
        boolean skipFirstColumn = columns.size() >= 3;
        for (int idx = Math.min(columns.size() - 1, 3); idx >= 0; idx--) {
            if (idx == 0 && skipFirstColumn) {
                continue;
            }
            String col = columns.get(idx);
            if (SUMMARY_ROW_PATTERN.matcher(col).find()) {
                continue;
            }
            Matcher matcher = BRACKET_WEIGHT.matcher(col);
            if (matcher.find()) {
                BigDecimal weight = new BigDecimal(matcher.group(1));
                String dim = BRACKET_WEIGHT.matcher(col).replaceAll("").trim();
                dim = dim.replace("\n", " ").replaceAll("\\s+", " ").trim();
                return new BracketWeightResult(weight, dim.isEmpty() ? null : dim, idx);
            }
        }
        return new BracketWeightResult(null, null, -1);
    }
    
    /**
     * 提取detail列。
     * 5列表格（code|factor|itemName|requirement|scoringRules）时，取列4+列5拼接。
     * 否则回退到原有逻辑（最长文本列）。
     */
    private static String extractDetail(List<String> columns, int codeIndex, int col3Index) {
        // 5列表格且列3是评分项名称列，则detail = 列4 + 列5
        if (columns.size() == 5 && col3Index == 2) {
            String col4 = columns.get(3).trim();
            String col5 = columns.get(4).trim();
            if (!col4.isEmpty() && !col5.isEmpty()) {
                return col4 + " " + col5;
            } else if (!col4.isEmpty()) {
                return col4;
            } else if (!col5.isEmpty()) {
                return col5;
            }
        }

        // 回退到原有逻辑：最长文本列
        int weightIndex = pureWeightIndex(columns, codeIndex);
        int detailIndex = detailColumnIndex(columns, codeIndex, weightIndex);
        if (detailIndex >= 0) {
            return columns.get(detailIndex);
        }

        // 最后兜底：整行原文
        return String.join(" ", columns);
    }
    
    /** 括号分值提取结果 */
    private record BracketWeightResult(BigDecimal weight, String dimFromCol3, int col3Index) {
    }
}
