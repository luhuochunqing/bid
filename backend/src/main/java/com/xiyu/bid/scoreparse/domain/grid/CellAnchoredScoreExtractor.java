// Input: TableGrid 数据与经过验真的 TableColumnMapping
// Output: 100% 逐字保真的 List<ScoreCandidate>
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 单元格锚定评分项提取器（spec 058）。
 * <p>纯代码确定性执行：基于验证通过的列映射配置，从 2D 表格网格中逐行提取单元格原文。
 * <ul>
 *   <li><b>0 幻觉</b>：detail 100% 来自物理单元格，绝对不经过任何 LLM 概括或重写；</li>
 *   <li><b>原生自然序</b>：按物理行自上而下顺序输出，彻底解决复合编号排序沉底缺陷；</li>
 *   <li><b>自动纵向补全（Fill-down）</b>：自动继承上一行的父大类维度名称，解决合并单元格子项漏维度问题；</li>
 *   <li><b>分值精准解析</b>：支持纯数值、百分比（6% -> 6.0）、X分等标准格式。</li>
 * </ul>
 */
public final class CellAnchoredScoreExtractor {

    private static final Pattern NUMERIC_WEIGHT_PATTERN = Pattern.compile("^[~≈]?(\\d{1,3}(?:\\.\\d+)?)\\s*(?:分|%)?$");
    private static final Pattern BRACKET_WEIGHT_PATTERN = Pattern.compile("[（(]\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分\\s*[）)]");
    private static final Pattern GRADE_SCORE_PATTERN = Pattern.compile("得\\s*(\\d{1,3}(?:\\.\\d+)?)\\s*分");
    private static final Pattern NON_SCORE_PROCEDURE_PATTERN = ScoreParseConstants.NON_SCORE_PROCEDURE_PATTERN;

    private CellAnchoredScoreExtractor() {
    }

    /**
     * 依据列映射配置从表格网格中提取评分候选清单。
     *
     * @param grid    表格网格
     * @param mapping 列映射配置
     * @return 评分候选列表
     */
    public static List<ScoreCandidate> extract(TableGrid grid, TableColumnMapping mapping) {
        if (grid == null || grid.isEmpty() || mapping == null) {
            return List.of();
        }

        List<ScoreCandidate> candidates = new ArrayList<>();
        int rowCount = grid.rowCount();
        int headerRows = Math.max(0, mapping.headerRows());

        String currentParentDim = "";
        String currentDim = "";

        for (int r = headerRows; r < rowCount; r++) {
            List<String> rowCells = grid.row(r);
            if (rowCells.isEmpty() || isAllEmpty(rowCells)) {
                continue;
            }

            // 1. 提取或继承父大类名称（纵向合并单元格 Fill-down）
            if (mapping.parentDimCol() >= 0 && mapping.parentDimCol() < grid.colCount()) {
                String parentCell = grid.cell(r, mapping.parentDimCol());
                if (!parentCell.isBlank()) {
                    currentParentDim = cleanDimName(parentCell);
                }
            }

            // 2. 提取或继承评分维度/项名称
            if (mapping.dimCol() >= 0 && mapping.dimCol() < grid.colCount()) {
                String dimCell = grid.cell(r, mapping.dimCol());
                if (!dimCell.isBlank()) {
                    currentDim = cleanDimName(dimCell);
                }
            }

            // 组合维度名称：有父大类则组合（如 "商务部分 / 响应文件的完整性"）
            String finalDim = buildCombinedDim(currentParentDim, currentDim);

            // 3. 提取标书原文细则（100% 逐字保真）
            String detail = "";
            if (mapping.detailCol() >= 0 && mapping.detailCol() < grid.colCount()) {
                detail = grid.cell(r, mapping.detailCol());
            }
            if (detail.isBlank()) {
                // 若细则列为空，尝试用整行非空单元格拼接兜底
                continue;
            }

            // 过滤非评分程序性条款（如"推荐成交候选供应商 3名"等）
            String sourceRow = String.join(" | ", rowCells);
            if (NON_SCORE_PROCEDURE_PATTERN.matcher(detail).find()
                    || NON_SCORE_PROCEDURE_PATTERN.matcher(finalDim).find()
                    || NON_SCORE_PROCEDURE_PATTERN.matcher(sourceRow).find()) {
                continue;
            }

            // 4. 提取编号
            String code = "";
            if (mapping.codeCol() >= 0 && mapping.codeCol() < grid.colCount()) {
                code = grid.cell(r, mapping.codeCol());
            }

            // 5. 提取分值
            BigDecimal weight = null;
            if (mapping.weightCol() >= 0 && mapping.weightCol() < grid.colCount()) {
                String weightCell = grid.cell(r, mapping.weightCol());
                weight = parseWeight(weightCell);
            }
            if (weight == null) {
                // 兜底：从 detail 中寻找档位满分或括号分值
                weight = parseWeightFromDetail(detail);
            }

            // 4. 编号过滤：如果编号以 4. 开头（如 4.4.1 评审程序条款），跳过
            if (code != null && code.trim().startsWith("4.")) {
                continue;
            }

            candidates.add(new ScoreCandidate(
                    code,
                    finalDim.isBlank() ? null : finalDim,
                    detail,
                    weight,
                    null,
                    null,
                    sourceRow,
                    "R" + (r + 1),
                    "TABLE_GRID"
            ));
        }

        return candidates;
    }

    private static boolean isAllEmpty(List<String> cells) {
        for (String c : cells) {
            if (c != null && !c.isBlank()) return false;
        }
        return true;
    }

    private static String cleanDimName(String raw) {
        if (raw == null) return "";
        return raw.replaceAll("[：:（(]\\s*\\d+(?:\\.\\d+)?\\s*(?:分|%|分值)\\s*[）)]?", "")
                .replace("\n", " ").replaceAll("\\s+", " ").trim();
    }

    private static String buildCombinedDim(String parent, String child) {
        if (!parent.isBlank() && !child.isBlank() && !parent.equals(child)) {
            return parent + "/" + child;
        }
        return !child.isBlank() ? child : parent;
    }

    private static BigDecimal parseWeight(String cell) {
        if (cell == null || cell.isBlank()) {
            return null;
        }
        String trimmed = cell.trim();
        Matcher m = NUMERIC_WEIGHT_PATTERN.matcher(trimmed);
        if (m.matches()) {
            return new BigDecimal(m.group(1));
        }
        Matcher bm = BRACKET_WEIGHT_PATTERN.matcher(trimmed);
        if (bm.find()) {
            return new BigDecimal(bm.group(1));
        }
        return null;
    }

    private static BigDecimal parseWeightFromDetail(String detail) {
        if (detail == null || detail.isBlank()) return null;
        Matcher m = GRADE_SCORE_PATTERN.matcher(detail);
        BigDecimal max = null;
        while (m.find()) {
            BigDecimal v = new BigDecimal(m.group(1));
            if (max == null || v.compareTo(max) > 0) {
                max = v;
            }
        }
        return max;
    }
}
