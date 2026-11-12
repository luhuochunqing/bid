package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.CellPayload;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 负责百度 OCR 单元格拓扑网格构建、坐标自适应收缩与续行关联。
 */
final class BaiduTableGridBuilder {

    private static final List<String> RULE_COL_KEYWORDS = List.of("评分标准", "评分细则", "评审标准", "评审细则", "标准", "细则", "内容", "要求");
    private static final List<String> SCORE_COL_KEYWORDS = List.of("分值", "得分", "满分", "权重", "分数");

    private BaiduTableGridBuilder() {}

    record SanitizedCell(int rStart, int rEnd, int cStart, int cEnd, int rowspan, int colspan, String text) {}

    record GridResult(
            List<CanonicalCellDto> cells,
            List<MergedRegionDto> mergedRegions,
            int rowCount,
            int colCount
    ) {}

    static GridResult buildCanonicalGrid(List<BaiduTableCell> rawCells, String tableId, int pageNum) {
        int maxRow = rawCells.stream().mapToInt(BaiduTableCell::rowStart).max().orElse(0);
        int maxCol = rawCells.stream().mapToInt(BaiduTableCell::colStart).max().orElse(0);
        int rowCount = maxRow + 1;
        int colCount = maxCol + 1;

        CanonicalCellDto[][] grid = new CanonicalCellDto[rowCount][colCount];
        List<MergedRegionDto> mergedRegions = new ArrayList<>();

        // 收集所有 OCR 识别出的起始单元格坐标
        Set<Long> originPositions = new HashSet<>();
        for (BaiduTableCell cell : rawCells) {
            originPositions.add(((long) cell.rowStart() << 32) | (cell.colStart() & 0xFFFFFFFFL));
        }

        // 检查并收缩有效 rowspan / colspan，防止 OCR 坐标重叠破坏拓扑结构
        List<SanitizedCell> sanitizedCells = sanitizeCells(rawCells, originPositions, maxRow, maxCol);

        // 放置起始单元格与覆盖占位单元格
        for (SanitizedCell cell : sanitizedCells) {
            int rStart = cell.rStart();
            int cStart = cell.cStart();
            int safeRowspan = cell.rowspan();
            int safeColspan = cell.colspan();
            String cellId = tableId + "_r" + rStart + "_c" + cStart;

            if (safeRowspan > 1 || safeColspan > 1) {
                mergedRegions.add(new MergedRegionDto(cellId, safeRowspan, safeColspan));
            }

            if (rStart < rowCount && cStart < colCount) {
                grid[rStart][cStart] = new CanonicalCellDto(
                        cellId, rStart, cStart, cell.text(), cell.text().trim(),
                        safeRowspan, safeColspan, false,
                        "pdf:page=" + pageNum + ";row=" + rStart + ";col=" + cStart,
                        pageNum, ""
                );
            }

            for (int r = rStart; r <= cell.rEnd() && r < rowCount; r++) {
                for (int c = cStart; c <= cell.cEnd() && c < colCount; c++) {
                    if (r == rStart && c == cStart) continue;
                    grid[r][c] = new CanonicalCellDto(
                            tableId + "_r" + r + "_c" + c, r, c,
                            cell.text(), cell.text().trim(), 1, 1, true,
                            "pdf:page=" + pageNum + ";row=" + r + ";col=" + c, pageNum, ""
                    );
                }
            }
        }

        // 补齐所有未覆盖的空白单元格
        fillEmptyCells(grid, rowCount, colCount, tableId, pageNum);

        // 动态识别表头角色与续行关系
        int ruleCol = detectRuleColumn(grid, rowCount, colCount);
        int scoreCol = detectScoreColumn(grid, rowCount, colCount);
        assignLogicalRowIds(grid, rowCount, colCount, tableId, ruleCol, scoreCol);

        List<CanonicalCellDto> cells = new ArrayList<>(rowCount * colCount);
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                cells.add(grid[r][c]);
            }
        }
        return new GridResult(List.copyOf(cells), List.copyOf(mergedRegions), rowCount, colCount);
    }

    private static List<SanitizedCell> sanitizeCells(List<BaiduTableCell> rawCells, Set<Long> originPositions,
                                                     int maxRow, int maxCol) {
        List<SanitizedCell> result = new ArrayList<>();
        for (BaiduTableCell cell : rawCells) {
            int rStart = cell.rowStart();
            int cStart = cell.colStart();
            int rEnd = Math.min(cell.rowEnd(), maxRow);
            int cEnd = Math.min(cell.colEnd(), maxCol);

            int safeCEnd = cStart;
            for (int c = cStart + 1; c <= cEnd; c++) {
                long key = ((long) rStart << 32) | (c & 0xFFFFFFFFL);
                if (originPositions.contains(key)) break;
                safeCEnd = c;
            }

            int safeREnd = rStart;
            for (int r = rStart + 1; r <= rEnd; r++) {
                boolean conflict = false;
                for (int c = cStart; c <= safeCEnd; c++) {
                    long key = ((long) r << 32) | (c & 0xFFFFFFFFL);
                    if (originPositions.contains(key)) {
                        conflict = true;
                        break;
                    }
                }
                if (conflict) break;
                safeREnd = r;
            }
            result.add(new SanitizedCell(rStart, safeREnd, cStart, safeCEnd,
                    safeREnd - rStart + 1, safeCEnd - cStart + 1, cell.words() != null ? cell.words() : ""));
        }
        return result;
    }

    private static void fillEmptyCells(CanonicalCellDto[][] grid, int rowCount, int colCount, String tableId, int pageNum) {
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                if (grid[r][c] == null) {
                    grid[r][c] = new CanonicalCellDto(
                            tableId + "_r" + r + "_c" + c, r, c, "", "", 1, 1, false,
                            "pdf:page=" + pageNum + ";row=" + r + ";col=" + c, pageNum, ""
                    );
                }
            }
        }
    }

    private static void assignLogicalRowIds(CanonicalCellDto[][] grid, int rowCount, int colCount,
                                            String tableId, int ruleCol, int scoreCol) {
        String lastLogicalRowId = "";
        for (int r = 0; r < rowCount; r++) {
            boolean isContinuation = isContinuationRow(grid[r], colCount, ruleCol, scoreCol, r);
            String currentLogicalRowId = (isContinuation && !lastLogicalRowId.isBlank()) ? lastLogicalRowId : tableId + "-r" + r;
            lastLogicalRowId = currentLogicalRowId;

            for (int c = 0; c < colCount; c++) {
                CanonicalCellDto old = grid[r][c];
                grid[r][c] = new CanonicalCellDto(
                        old.cellId(), old.row(), old.column(), old.rawText(), old.normalizedText(),
                        old.rowspan(), old.colspan(), old.isMergedContinue(), old.sourceXmlPath(),
                        old.sourcePage(), currentLogicalRowId
                );
            }
        }
    }

    private static int detectRuleColumn(CanonicalCellDto[][] grid, int rowCount, int colCount) {
        if (rowCount == 0 || colCount <= 1) return Math.max(0, colCount - 1);
        int headerLimit = Math.min(rowCount, 3);
        for (int r = 0; r < headerLimit; r++) {
            for (int c = 0; c < colCount; c++) {
                String text = grid[r][c].normalizedText();
                for (String kw : RULE_COL_KEYWORDS) {
                    if (text.contains(kw)) return c;
                }
            }
        }
        return colCount >= 2 ? colCount - 2 : colCount - 1;
    }

    private static int detectScoreColumn(CanonicalCellDto[][] grid, int rowCount, int colCount) {
        if (rowCount == 0 || colCount <= 1) return colCount - 1;
        int headerLimit = Math.min(rowCount, 3);
        for (int r = 0; r < headerLimit; r++) {
            for (int c = 0; c < colCount; c++) {
                String text = grid[r][c].normalizedText();
                for (String kw : SCORE_COL_KEYWORDS) {
                    if (text.contains(kw)) return c;
                }
            }
        }
        return colCount - 1;
    }

    private static boolean isContinuationRow(CanonicalCellDto[] rowCells, int colCount, int ruleCol, int scoreCol, int rowIndex) {
        if (rowIndex == 0 || ruleCol < 0 || ruleCol >= colCount) return false;
        String ruleText = rowCells[ruleCol].normalizedText();
        if (ruleText.isBlank()) return false;

        boolean prefixEmpty = true;
        for (int c = 0; c < ruleCol; c++) {
            if (!rowCells[c].normalizedText().isBlank() && !Boolean.TRUE.equals(rowCells[c].isMergedContinue())) {
                prefixEmpty = false;
                break;
            }
        }

        boolean scoreEmpty = scoreCol >= 0 && scoreCol < colCount
                && (rowCells[scoreCol].normalizedText().isBlank() || Boolean.TRUE.equals(rowCells[scoreCol].isMergedContinue()));
        if (!scoreEmpty && colCount >= 3 && !rowCells[1].normalizedText().isBlank()) return false;
        if (prefixEmpty && (scoreEmpty || scoreCol == ruleCol)) return true;

        for (int c = 0; c < colCount; c++) {
            if (c != ruleCol && !rowCells[c].normalizedText().isBlank()) return false;
        }
        return true;
    }

    static List<List<CellPayload>> buildSimplePayloadGrid(List<BaiduTableCell> cells) {
        int maxRow = cells.stream().mapToInt(BaiduTableCell::rowStart).max().orElse(0);
        int maxCol = cells.stream().mapToInt(BaiduTableCell::colStart).max().orElse(0);
        int rowCount = maxRow + 1;
        int colCount = maxCol + 1;

        CellPayload[][] grid = new CellPayload[rowCount][colCount];
        for (BaiduTableCell c : cells) {
            int rStart = c.rowStart();
            int rEnd = Math.min(c.rowEnd(), maxRow);
            int cStart = c.colStart();
            int cEnd = Math.min(c.colEnd(), maxCol);
            String text = c.words() != null ? c.words() : "";
            int safeRowspan = Math.max(1, rEnd - rStart + 1);
            int safeColspan = Math.max(1, cEnd - cStart + 1);

            if (rStart < rowCount && cStart < colCount) {
                grid[rStart][cStart] = new CellPayload(text, safeRowspan, safeColspan, false);
            }
            for (int r = rStart; r <= rEnd && r < rowCount; r++) {
                for (int col = cStart; col <= cEnd && col < colCount; col++) {
                    if (r == rStart && col == cStart) continue;
                    grid[r][col] = new CellPayload("", safeRowspan, safeColspan, true);
                }
            }
        }

        List<List<CellPayload>> result = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            List<CellPayload> rowList = new ArrayList<>(colCount);
            for (int col = 0; col < colCount; col++) {
                CellPayload p = grid[r][col];
                rowList.add(p != null ? p : new CellPayload("", 1, 1, false));
            }
            result.add(List.copyOf(rowList));
        }
        return List.copyOf(result);
    }
}
