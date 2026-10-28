package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;

import java.util.ArrayList;
import java.util.List;

/**
 * 负责相邻跨页的百度 OCR 表格逻辑行连续性自动衔接。
 */
final class BaiduCrossPageTableLinker {

    private BaiduCrossPageTableLinker() {}

    /**
     * 对相邻跨页的百度 OCR 表格，自动将跨页第一行续行的 logicalRowId 衔接到前一页最后一行。
     */
    static List<CanonicalTable> linkCrossPageLogicalRows(List<CanonicalTable> tables) {
        if (tables == null || tables.size() <= 1) {
            return tables == null ? List.of() : tables;
        }

        List<CanonicalTable> result = new ArrayList<>(tables);
        for (int i = 1; i < result.size(); i++) {
            CanonicalTable prev = result.get(i - 1);
            CanonicalTable curr = result.get(i);
            if (prev.sourcePage() == null || curr.sourcePage() == null) continue;
            if (curr.sourcePage() - prev.sourcePage() != 1) continue;

            String lastLogicalRowId = "";
            for (int r = prev.rows() - 1; r >= 0; r--) {
                final int rowIdx = r;
                var cell = prev.cells().stream()
                        .filter(c -> c.row() == rowIdx && c.logicalRowId() != null && !c.logicalRowId().isBlank())
                        .findFirst();
                if (cell.isPresent()) {
                    lastLogicalRowId = cell.get().logicalRowId();
                    break;
                }
            }
            if (lastLogicalRowId.isBlank()) continue;

            if (isFirstRowContinuation(curr)) {
                String targetLogicalRowId = curr.tableId() + "-r0";
                List<CanonicalCellDto> updatedCells = new ArrayList<>();
                for (CanonicalCellDto c : curr.cells()) {
                    if (targetLogicalRowId.equals(c.logicalRowId())) {
                        updatedCells.add(new CanonicalCellDto(
                                c.cellId(), c.row(), c.column(), c.rawText(), c.normalizedText(),
                                c.rowspan(), c.colspan(), c.isMergedContinue(), c.sourceXmlPath(),
                                c.sourcePage(), lastLogicalRowId
                        ));
                    } else {
                        updatedCells.add(c);
                    }
                }
                result.set(i, new CanonicalTable(
                        curr.tableId(), curr.sourcePage(), curr.sourceSection(),
                        curr.rows(), curr.columns(), curr.mergedRegions(),
                        updatedCells, curr.isScoreTable(), curr.sourceFileType()
                ));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isFirstRowContinuation(CanonicalTable table) {
        if (table.rows() == 0 || table.columns() <= 1) return false;
        List<CanonicalCellDto> row0Cells = table.cells().stream().filter(c -> c.row() == 0).toList();
        if (row0Cells.isEmpty()) return false;

        int colCount = table.columns();
        int ruleCol = colCount <= 2 ? colCount - 1 : colCount - 2;

        for (int c = 0; c < ruleCol; c++) {
            final int colIdx = c;
            var cell = row0Cells.stream().filter(cellDto -> cellDto.column() == colIdx).findFirst();
            if (cell.isPresent() && !cell.get().normalizedText().isBlank() && !Boolean.TRUE.equals(cell.get().isMergedContinue())) {
                return false;
            }
        }
        final int targetRuleCol = ruleCol;
        return row0Cells.stream().anyMatch(c -> c.column() == targetRuleCol && !c.normalizedText().isBlank());
    }
}
