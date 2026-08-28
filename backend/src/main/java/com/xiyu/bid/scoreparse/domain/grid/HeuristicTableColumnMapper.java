// Input: TableGrid 数据
// Output: 启发式推断出的 TableColumnMapping
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 启发式表格列映射推断器（spec 058）。
 * <p>纯规则静态分析表头文本与数据分布，作为无 LLM 环境的基准或兜底推断引擎。
 */
public final class HeuristicTableColumnMapper {

    private static final Pattern CODE_HEADER = Pattern.compile("序号|条款号|编号|编码|条目|条号");
    private static final Pattern PARENT_DIM_HEADER = Pattern.compile("评审大类|大类|评审类别|标包|分包");
    private static final Pattern DIM_HEADER = Pattern.compile("评分因素|评分项|评审因素|评审项目|维度|项目名称|指标名称");
    private static final Pattern DETAIL_HEADER = Pattern.compile("评分标准|评分细则|评审标准|详细细则|打分细则|打分标准|条款内容");
    private static final Pattern WEIGHT_HEADER = Pattern.compile("分值|权重|满分|最高分|得分|比例");

    private HeuristicTableColumnMapper() {
    }

    public static TableColumnMapping infer(TableGrid grid) {
        if (grid == null || grid.isEmpty()) {
            return TableColumnMapping.ofStandard(0, 1, 2, 3);
        }

        int colCount = grid.colCount();
        List<String> headerRow = grid.row(0);

        int codeCol = -1;
        int parentDimCol = -1;
        int dimCol = -1;
        int detailCol = -1;
        int weightCol = -1;

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

        // 兜底策略：如果关键列未识别
        if (detailCol == -1) {
            // 细则列通常是文本最宽、平均长度最长的一列
            int maxAvgLen = -1;
            int longestCol = -1;
            for (int c = 0; c < colCount; c++) {
                if (c == codeCol || c == weightCol) continue;
                int totalLen = 0;
                for (int r = 1; r < grid.rowCount(); r++) {
                    totalLen += grid.cell(r, c).length();
                }
                if (totalLen > maxAvgLen) {
                    maxAvgLen = totalLen;
                    longestCol = c;
                }
            }
            detailCol = longestCol >= 0 ? longestCol : (colCount > 2 ? 1 : 0);
        }

        if (weightCol == -1) {
            // 权重列通常在最后一列
            weightCol = colCount - 1;
        }

        if (dimCol == -1 && detailCol > 0 && detailCol != codeCol) {
            // 寻找细则列前面的非编号列作为维度列
            for (int c = 0; c < detailCol; c++) {
                if (c != codeCol && c != parentDimCol) {
                    dimCol = c;
                    break;
                }
            }
        }

        // 检测权重列是否为百分比格式
        boolean isPercentage = false;
        for (int r = 1; r < grid.rowCount(); r++) {
            if (grid.cell(r, weightCol).contains("%")) {
                isPercentage = true;
                break;
            }
        }

        return new TableColumnMapping(codeCol, parentDimCol, dimCol, detailCol, weightCol, 1, isPercentage);
    }
}
