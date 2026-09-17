// Input: TableGrid 数据
// Output: 启发式推断出的 TableColumnMapping
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 启发式表格列映射推断器（spec 058）。
 * <p>纯规则静态分析表头文本与数据分布，作为无 LLM 环境的基准或兜底推断引擎。
 */
public final class HeuristicTableColumnMapper {

    private static final Pattern CODE_HEADER = Pattern.compile("序号|条款号|编号|编码|条目|条号");
    private static final Pattern PARENT_DIM_HEADER = Pattern.compile("评审大类|大类|评审类别|标包|分包|^项目$|评审大项");
    private static final Pattern DIM_HEADER = Pattern.compile("评分因素|评分项|评审因素|评审项目|评审要素|维度|项目名称|指标名称");
    private static final Pattern DETAIL_HEADER = Pattern.compile("评分标准|评分细则|评审标准|详细细则|打分细则|打分标准|条款内容|评审内容|评分内容");
    private static final Pattern WEIGHT_HEADER = Pattern.compile("分值|权重|满分|最高分|得分|比例");
    private static final Pattern BRACKET_WEIGHT_PATTERN = ScoreParseConstants.BRACKET_WEIGHT_PATTERN;
    private static final Pattern CONTRACT_ASSESSMENT_HEADER = ScoreParseConstants.CONTRACT_ASSESSMENT_PATTERN;
    private static final Pattern NON_SCORE_CATALOG_HEADER = ScoreParseConstants.NON_SCORE_CATALOG_PATTERN;
    private static final Pattern PACKAGE_LOT_HEADER = Pattern.compile("分标名称|分标编号|包名称|标包划分|标段划分");
    private static final Pattern EXPLICIT_BID_SCORE_HEADER = Pattern.compile(
            "评分因素|评分标准|评审因素|评审标准|评标办法|分值构成|商务评分|技术评分|报价评分"
    );

    private HeuristicTableColumnMapper() {
    }

    public static TableColumnMapping infer(TableGrid grid) {
        if (grid == null || grid.isEmpty()) {
            return TableColumnMapping.ofStandard(0, 1, 2, 3);
        }

        int headerRowIndex = 0;
        int maxHeaderMatches = countHeaderMatches(grid.row(0));

        // 若首行已具备明确表头特征且无超长文本，直接以首行为表头；
        // 仅当首行明显不是表头（如横幅/说明行 matches < 2 或含超长文本）时，才在前 3 行中向下探测真实表头。
        if (maxHeaderMatches < 2 || hasOverlyLongCell(grid.row(0))) {
            int maxHeaderScan = Math.min(grid.rowCount(), 3);
            for (int r = 1; r < maxHeaderScan; r++) {
                List<String> candidateRow = grid.row(r);
                if (!hasOverlyLongCell(candidateRow)) {
                    int matches = countHeaderMatches(candidateRow);
                    if (matches >= 2) {
                        headerRowIndex = r;
                        maxHeaderMatches = matches;
                        break;
                    }
                }
            }
        }

        List<String> headerRow = grid.row(headerRowIndex);
        String allHeaders = String.join(" ", headerRow);
        boolean isExplicitScoreTable = EXPLICIT_BID_SCORE_HEADER.matcher(allHeaders).find();
        if (!isExplicitScoreTable) {
            for (int r = 0; r < headerRowIndex; r++) {
                if (EXPLICIT_BID_SCORE_HEADER.matcher(String.join(" ", grid.row(r))).find()) {
                    isExplicitScoreTable = true;
                    break;
                }
            }
        }
        if (!isExplicitScoreTable) {
            if (CONTRACT_ASSESSMENT_HEADER.matcher(allHeaders).find()
                    || NON_SCORE_CATALOG_HEADER.matcher(allHeaders).find()
                    || PACKAGE_LOT_HEADER.matcher(allHeaders).find()
                    || containsAssessmentPattern(grid)) {
                return new TableColumnMapping(-1, -1, -1, -1, -1, grid.rowCount(), false);
            }
        }

        boolean isGenuineHeader = maxHeaderMatches >= 2
                || (!hasOverlyLongCell(headerRow)
                && (CODE_HEADER.matcher(allHeaders).find()
                || DIM_HEADER.matcher(allHeaders).find()
                || DETAIL_HEADER.matcher(allHeaders).find()
                || WEIGHT_HEADER.matcher(allHeaders).find()));
        int headerRows = isGenuineHeader ? (headerRowIndex + 1) : 0;

        int colCount = grid.colCount();

        int codeCol = -1;
        int parentDimCol = -1;
        int dimCol = -1;
        int detailCol = -1;
        int weightCol = -1;

        if (isGenuineHeader) {
            for (int c = 0; c < headerRow.size(); c++) {
                String colName = headerRow.get(c);
                if (CODE_HEADER.matcher(colName).find() && codeCol == -1) {
                    codeCol = c;
                } else if (PARENT_DIM_HEADER.matcher(colName).find() && parentDimCol == -1) {
                    parentDimCol = c;
                } else if (DETAIL_HEADER.matcher(colName).find() && detailCol == -1) {
                    detailCol = c;
                } else if (WEIGHT_HEADER.matcher(colName).find() && weightCol == -1) {
                    weightCol = c;
                } else if (DIM_HEADER.matcher(colName).find() && dimCol == -1) {
                    dimCol = c;
                }
            }
        }

        // 兜底策略：如果细则列未识别，选择数据最长的一列
        if (detailCol == -1) {
            int maxAvgLen = -1;
            int longestCol = -1;
            for (int c = 0; c < colCount; c++) {
                if (c == codeCol || c == weightCol) continue;
                int totalLen = 0;
                for (int r = headerRows; r < grid.rowCount(); r++) {
                    totalLen += grid.cell(r, c).length();
                }
                if (totalLen > maxAvgLen) {
                    maxAvgLen = totalLen;
                    longestCol = c;
                }
            }
            detailCol = longestCol >= 0 ? longestCol : (colCount > 2 ? 1 : 0);
        }

        // 寻找内嵌括号分值列（如"供应商业绩（7分）"）。取首个（最左）命中列锚定。
        // 命中门槛权衡（xcode-review 实测）：曾试验 count>=2 防单格偶发文本劫持，但 MRO 真实
        // 标书分页把评分表切成碎片块（首行是上一页细则残行），括号列常仅 1 格命中，门槛会整块
        // 丢弃正确评分项——损害大于劫持风险，故维持 count>0，单格劫持由 ColumnTypeCrossVerifier
        // 的权重列密度地板（0.4）兜底拒绝。
        int bracketCol = -1;
        for (int c = 0; c < colCount; c++) {
            if (c == detailCol) continue;
            int count = 0;
            for (int r = headerRows; r < grid.rowCount(); r++) {
                if (BRACKET_WEIGHT_PATTERN.matcher(grid.cell(r, c)).find()) {
                    count++;
                }
            }
            if (count > 0) {
                bracketCol = c;
                break;
            }
        }

        if (bracketCol >= 0) {
            if (weightCol == -1) {
                weightCol = bracketCol;
            }
            if (parentDimCol == -1 && dimCol >= 0 && dimCol < bracketCol) {
                parentDimCol = dimCol;
                dimCol = bracketCol;
            } else if (dimCol == -1 || dimCol == detailCol) {
                dimCol = bracketCol;
            }
            // parentDim 推断防御：左侧邻列若恰是细则列或编号列，父维度会读到长细则/编号文本，
            // 宁缺勿错（复审 Finding 9：codeCol 边界）
            if (parentDimCol == -1 && bracketCol >= 1 && bracketCol != codeCol
                    && bracketCol - 1 != detailCol && bracketCol - 1 != codeCol) {
                parentDimCol = bracketCol - 1;
            }
        }

        if (weightCol == -1) {
            weightCol = colCount - 1;
        }

        if (dimCol == -1 && detailCol > 0 && detailCol != codeCol) {
            for (int c = 0; c < detailCol; c++) {
                if (c != codeCol && c != parentDimCol) {
                    dimCol = c;
                    break;
                }
            }
        }

        // 检测权重列是否为百分比格式
        boolean isPercentage = false;
        for (int r = headerRows; r < grid.rowCount(); r++) {
            if (grid.cell(r, weightCol).contains("%")) {
                isPercentage = true;
                break;
            }
        }

        return new TableColumnMapping(codeCol, parentDimCol, dimCol, detailCol, weightCol, headerRows, isPercentage);
    }

    private static int countHeaderMatches(List<String> row) {
        if (row == null || row.isEmpty()) {
            return 0;
        }
        int count = 0;
        boolean hasCode = false;
        boolean hasParent = false;
        boolean hasDim = false;
        boolean hasDetail = false;
        boolean hasWeight = false;
        for (String cell : row) {
            if (cell == null || cell.isBlank()) continue;
            if (!hasCode && CODE_HEADER.matcher(cell).find()) {
                hasCode = true;
                count++;
            }
            if (!hasParent && PARENT_DIM_HEADER.matcher(cell).find()) {
                hasParent = true;
                count++;
            }
            if (!hasDim && DIM_HEADER.matcher(cell).find()) {
                hasDim = true;
                count++;
            }
            if (!hasDetail && DETAIL_HEADER.matcher(cell).find()) {
                hasDetail = true;
                count++;
            }
            if (!hasWeight && WEIGHT_HEADER.matcher(cell).find()) {
                hasWeight = true;
                count++;
            }
        }
        return count;
    }

    private static boolean hasOverlyLongCell(List<String> row) {
        if (row == null) return false;
        for (String cell : row) {
            if (cell != null && cell.length() > 40) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsAssessmentPattern(TableGrid grid) {
        for (int r = 0; r < grid.rowCount(); r++) {
            for (String cell : grid.row(r)) {
                if (cell != null && CONTRACT_ASSESSMENT_HEADER.matcher(cell).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
