// Input: sidecar /extract-tables 响应 JSON
// Output: 结构化表格提取结果值对象（表格清单 + 警告）
// Pos: scoreparse/domain — Phase 1 结构化表格提取通道（纯值对象，零框架依赖）
package com.xiyu.bid.scoreparse.domain;

import java.util.List;

/**
 * sidecar /extract-tables 响应结果。
 * <p>每个 {@link TablePayload} 包含行-列二维网格（含合并单元格信息）和评分表启发式标记。
 */
public record SidecarTableExtraction(List<TablePayload> tables, List<String> warnings) {

    public SidecarTableExtraction {
        tables = tables == null ? List.of() : List.copyOf(tables);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static SidecarTableExtraction empty() {
        return new SidecarTableExtraction(List.of(), List.of());
    }

    /** 单个表格的载荷。 */
    public record TablePayload(int tableIndex, List<List<CellPayload>> rows, boolean isScoreTable) {
        public TablePayload {
            rows = rows == null ? List.of() : List.copyOf(rows);
        }
    }

    /** 单个单元格的载荷（含合并单元格信息）。 */
    public record CellPayload(String text, int rowspan, int colspan, boolean isMergedContinue) {
        public CellPayload {
            if (text == null) text = "";
        }
    }
}
