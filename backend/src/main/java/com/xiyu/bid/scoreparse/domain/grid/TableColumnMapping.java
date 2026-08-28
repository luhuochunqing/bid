// Input: 表格列语义映射定义
// Output: 结构化 TableColumnMapping 配置
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖
// 维护声明: 维护者按项目SOP；spec 058 单元格锚定评分解析

package com.xiyu.bid.scoreparse.domain.grid;

/**
 * 表格列语义映射（spec 058）。
 *
 * @param codeCol          编号列索引（-1 表示无编号列）
 * @param parentDimCol    父大类列索引（-1 表示无父大类列，用于纵向合并单元格如"商务部分"）
 * @param dimCol          评分因素/子项名称列索引（-1 表示无独立项名列）
 * @param detailCol       标书原文/评分细则列索引（必须 >= 0）
 * @param weightCol       权重/分值列索引（必须 >= 0）
 * @param headerRows      表头占用行数（跳过表头，默认 1）
 * @param isPercentage    权重是否为百分比格式（如 6% -> 6.0）
 */
public record TableColumnMapping(
        int codeCol,
        int parentDimCol,
        int dimCol,
        int detailCol,
        int weightCol,
        int headerRows,
        boolean isPercentage
) {

    public TableColumnMapping {
        if (headerRows < 0) {
            headerRows = 1;
        }
    }

    public static TableColumnMapping ofStandard(int codeCol, int dimCol, int detailCol, int weightCol) {
        return new TableColumnMapping(codeCol, -1, dimCol, detailCol, weightCol, 1, false);
    }
}
