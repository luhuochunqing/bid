// Input: Sidecar DocumentArtifact schemaVersion=2.0/2.1 JSON
// Output: 不可变 CanonicalTableGrid 结构 + 结构验证结果
// Pos: scoreparse/domain/grid — 纯核心，无框架依赖（spec 059 Phase 1）
package com.xiyu.bid.scoreparse.domain.grid;

import java.util.List;
import java.util.Optional;

/**
 * 结构化表格的规范网格（spec 059 Phase 1）。
 * <p>取代 {@code TableGrid} 的字符串矩阵形态：每个 cell 携带逻辑坐标、原始/规范化双文本、
 * 合并来源（origin cell id）与 XML 锚点。提供给后续 ScoreFact 提取时使用。
 * <p>本模型严格不可变，所有字段在构造时即固定；后续校验失败必须重新构造一个
 * 包含 {@link StructureValidationResult#empty()} 的网格并通过
 * {@link CanonicalTableGrid#validation()} 暴露。
 */
public record CanonicalTableGrid(
        String tableId,
        int rows,
        int columns,
        List<CanonicalCell> cells,
        List<MergedRegion> mergedRegions,
        StructureValidationResult validation
) {
    public CanonicalTableGrid {
        tableId = tableId == null ? "" : tableId;
        cells = cells == null ? List.of() : List.copyOf(cells);
        mergedRegions = mergedRegions == null ? List.of() : List.copyOf(mergedRegions);
        validation = validation == null ? StructureValidationResult.empty() : validation;
    }

    /** 查找指定 cell id 对应的 cell。 */
    public Optional<CanonicalCell> findCell(String cellId) {
        if (cellId == null) {
            return Optional.empty();
        }
        for (CanonicalCell cell : cells) {
            if (cellId.equals(cell.cellId())) {
                return Optional.of(cell);
            }
        }
        return Optional.empty();
    }

    /** 收集 row 行的所有 cell（包括合并延续）。 */
    public List<CanonicalCell> cellsInRow(int row) {
        return cells.stream().filter(c -> c.row() == row).toList();
    }

    public boolean isStructurallyValid() {
        return validation.valid();
    }
}
