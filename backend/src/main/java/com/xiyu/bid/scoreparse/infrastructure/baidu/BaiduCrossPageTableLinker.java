package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 负责相邻跨页的百度 OCR 表格逻辑行连续性自动衔接与无表头续表合并。
 */
final class BaiduCrossPageTableLinker {

    private static final Pattern HEADER_KEYWORD = Pattern.compile("(?:条款号|序号|编号|评审因素|评分因素|评审项目|评分项|评审标准|评分标准|评分细则)");

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
            if (curr.sourcePage() - maxPage(prev) != 1) continue;

            String lastLogicalRowId = findLastNonBlankLogicalRowId(prev);
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

    /**
     * 将相邻页面上同列数、无重复表头的跨页评分续表合并入主评分表，保持单一 tableId 与表头结构。
     */
    static List<CanonicalTable> mergeCrossPageScoreTables(List<CanonicalTable> tables) {
        return mergeCrossPageScoreTables(tables, java.util.Map.of());
    }

    static List<CanonicalTable> mergeCrossPageScoreTables(List<CanonicalTable> tables,
            java.util.Map<Integer, CanonicalTable> originalOwners) {
        if (tables == null || tables.size() <= 1) {
            return tables == null ? List.of() : tables;
        }
        List<CanonicalTable> merged = new ArrayList<>();
        for (CanonicalTable curr : tables) {
            int prevScoreIdx = findMergeablePreviousScoreTable(merged, curr, originalOwners);
            if (prevScoreIdx < 0) {
                merged.add(curr);
                continue;
            }
            CanonicalTable prev = merged.get(prevScoreIdx);
            merged.set(prevScoreIdx, appendContinuationTable(prev, curr, hasHeaderRow(curr)));
        }
        return List.copyOf(merged);
    }

    private static int findMergeablePreviousScoreTable(List<CanonicalTable> merged, CanonicalTable curr,
            java.util.Map<Integer, CanonicalTable> originalOwners) {
        if (!Boolean.TRUE.equals(curr.isScoreTable()) || curr.sourcePage() == null) {
            return -1;
        }
        for (int i = merged.size() - 1; i >= 0; i--) {
            CanonicalTable candidate = merged.get(i);
            if (!Boolean.TRUE.equals(candidate.isScoreTable()) || candidate.sourcePage() == null) continue;
            boolean repeatedHeader = hasHeaderRow(curr);
            var owner = originalOwners.get(curr.sourcePage());
            boolean trusted = owner != null && owner == originalOwners.get(maxPage(candidate))
                    && owner.sourceSection().equals(curr.sourceSection());
            if (curr.sourcePage() - maxPage(candidate) == 1 && candidate.columns() == curr.columns()
                    && (!repeatedHeader || (trusted && BaiduRepeatedHeaderPolicy.isContinuation(candidate, curr)))) {
                return i;
            }
            if (repeatedHeader) break; // Never jump across a genuine scoring table.
        }
        return -1;
    }

    private static CanonicalTable appendContinuationTable(CanonicalTable prev, CanonicalTable curr, boolean repeatedHeader) {
        int skippedRows = repeatedHeader ? 1 : 0;
        int rowOffset = prev.rows() - skippedRows;
        String lastLogicalRowId = findLastNonBlankLogicalRowId(prev);
        boolean firstRowCont = !lastLogicalRowId.isBlank() && (repeatedHeader || isFirstRowContinuation(curr));
        String currRow0LogicalId = curr.tableId() + "-r" + skippedRows;
        String currLogicalPrefix = curr.tableId() + "-r";

        List<CanonicalCellDto> combinedCells = new ArrayList<>(prev.cells());
        for (CanonicalCellDto c : curr.cells()) {
            if (repeatedHeader && Integer.valueOf(0).equals(c.row())) continue;
            int newRow = (c.row() == null ? 0 : c.row()) + rowOffset;
            String newCellId = prev.tableId() + "_r" + newRow + "_c" + c.column();
            String oldLogical = c.logicalRowId() == null ? "" : c.logicalRowId();
            String newLogical;
            if (firstRowCont && currRow0LogicalId.equals(oldLogical)) {
                newLogical = lastLogicalRowId;
            } else if (oldLogical.startsWith(currLogicalPrefix)) {
                int origRow = Integer.parseInt(oldLogical.substring(currLogicalPrefix.length()));
                newLogical = prev.tableId() + "-r" + (origRow + rowOffset);
            } else {
                newLogical = prev.tableId() + "-r" + newRow;
            }
            Integer cellPage = c.sourcePage() != null ? c.sourcePage() : curr.sourcePage();
            combinedCells.add(new CanonicalCellDto(
                    newCellId, newRow, c.column(), c.rawText(), c.normalizedText(),
                    c.rowspan(), c.colspan(), c.isMergedContinue(), c.sourceXmlPath(),
                    cellPage, newLogical
            ));
        }

        List<MergedRegionDto> combinedRegions = new ArrayList<>(prev.mergedRegions());
        String cellPrefix = curr.tableId() + "_r";
        for (MergedRegionDto reg : curr.mergedRegions()) {
            if (repeatedHeader && curr.cells().stream().anyMatch(c -> Integer.valueOf(0).equals(c.row())
                    && c.cellId().equals(reg.startCellId()))) continue;
            String startId = reg.startCellId();
            if (startId != null && startId.startsWith(cellPrefix)) {
                int cPos = startId.indexOf("_c", cellPrefix.length());
                if (cPos > cellPrefix.length()) {
                    int origRow = Integer.parseInt(startId.substring(cellPrefix.length(), cPos));
                    startId = prev.tableId() + "_r" + (origRow + rowOffset) + startId.substring(cPos);
                }
            }
            combinedRegions.add(new MergedRegionDto(startId, reg.rowspan(), reg.colspan()));
        }

        return new CanonicalTable(
                prev.tableId(), prev.sourcePage(), prev.sourceSection(),
                prev.rows() + curr.rows() - skippedRows, prev.columns(),
                List.copyOf(combinedRegions), List.copyOf(combinedCells),
                prev.isScoreTable(), prev.sourceFileType()
        );
    }

    private static int maxPage(CanonicalTable table) {
        int max = table.sourcePage() == null ? 0 : table.sourcePage();
        for (CanonicalCellDto c : table.cells()) {
            if (c.sourcePage() != null && c.sourcePage() > max) {
                max = c.sourcePage();
            }
        }
        return max;
    }

    private static String findLastNonBlankLogicalRowId(CanonicalTable table) {
        for (int r = table.rows() - 1; r >= 0; r--) {
            final int rowIdx = r;
            List<CanonicalCellDto> rowCells = table.cells().stream().filter(c -> c.row() == rowIdx).toList();
            boolean hasText = rowCells.stream().anyMatch(c -> c.normalizedText() != null && !c.normalizedText().isBlank());
            if (!hasText) continue;
            var cell = rowCells.stream()
                    .filter(c -> c.logicalRowId() != null && !c.logicalRowId().isBlank())
                    .findFirst();
            if (cell.isPresent()) {
                return cell.get().logicalRowId();
            }
        }
        return "";
    }

    private static boolean hasHeaderRow(CanonicalTable table) {
        long headerCells = table.cells().stream()
                .filter(c -> Integer.valueOf(0).equals(c.row()) && c.normalizedText() != null)
                .filter(c -> HEADER_KEYWORD.matcher(c.normalizedText().replaceAll("\\s+", "")).matches())
                .count();
        return headerCells >= 2;
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
