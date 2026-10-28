package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.CellPayload;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.TablePayload;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableItem;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;

import java.util.ArrayList;
import java.util.List;

/**
 * 将百度表格识别响应映射转换为系统内部通用模型（SidecarTableExtraction / DocumentArtifact）。
 * <p>遵循 FP-Java 规范：纯函数式映射，无外部状态依赖。
 */
public final class BaiduTableArtifactAdapter {

    private static final List<String> SCORE_TABLE_KEYWORDS = List.of(
            "评分", "分值", "标准", "得分", "满分", "权重", "技术标", "商务标", "评标"
    );

    private BaiduTableArtifactAdapter() {}

    /**
     * 将百度表格识别结果转换为系统标准的 SidecarTableExtraction。
     */
    public static SidecarTableExtraction toSidecarTableExtraction(BaiduTableResponse response) {
        if (response == null || !response.isSuccess() || response.tablesResult().isEmpty()) {
            List<String> warnings = response != null && response.errorMsg() != null
                    ? List.of("Baidu OCR Error: " + response.errorMsg())
                    : List.of();
            return new SidecarTableExtraction(List.of(), warnings);
        }

        List<TablePayload> tables = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        for (int i = 0; i < response.tablesResult().size(); i++) {
            BaiduTableItem item = response.tablesResult().get(i);
            if (item.body().isEmpty()) {
                continue;
            }

            List<List<CellPayload>> rows = buildGrid(item.body());
            boolean isScore = isLikelyScoreTable(item);
            tables.add(new TablePayload(i, rows, isScore));

            // 如果有被识别出的页眉页脚元数据，记录到 warnings
            if (!item.header().isEmpty()) {
                warnings.add("Table " + i + " header metadata: " + item.header().stream().map(h -> h.words()).toList());
            }
            if (!item.footer().isEmpty()) {
                warnings.add("Table " + i + " footer metadata: " + item.footer().stream().map(f -> f.words()).toList());
            }
        }

        return new SidecarTableExtraction(tables, warnings);
    }

    /**
     * 将百度表格识别结果转换为 DocumentArtifact.CanonicalTable 列表。
     */
    public static DocumentArtifact toDocumentArtifact(BaiduTableResponse response, String documentId, int pageNum) {
        return toDocumentArtifact(response, documentId, pageNum, null);
    }

    /**
     * 将百度表格识别结果转换为 DocumentArtifact，支持传入所属章节。
     */
    public static DocumentArtifact toDocumentArtifact(BaiduTableResponse response, String documentId, int pageNum, String sourceSection) {
        List<CanonicalTable> canonicalTables = toCanonicalTables(response, pageNum, sourceSection);
        ArtifactMeta meta = new ArtifactMeta(documentId, "", "PDF", "baidu-ocr");
        return new DocumentArtifact(meta, canonicalTables, List.of());
    }

    /**
     * 将百度表格识别结果映射为标准的 CanonicalTable 列表。
     */
    public static List<CanonicalTable> toCanonicalTables(BaiduTableResponse response, int pageNum, String sourceSection) {
        if (response == null || !response.isSuccess() || response.tablesResult().isEmpty()) {
            return List.of();
        }

        List<CanonicalTable> canonicalTables = new ArrayList<>();
        for (int tIdx = 0; tIdx < response.tablesResult().size(); tIdx++) {
            BaiduTableItem item = response.tablesResult().get(tIdx);
            if (item.body().isEmpty()) {
                continue;
            }

            int maxRow = item.body().stream().mapToInt(BaiduTableCell::rowEnd).max().orElse(0);
            int maxCol = item.body().stream().mapToInt(BaiduTableCell::colEnd).max().orElse(0);
            int rowCount = maxRow + 1;
            int colCount = maxCol + 1;

            String tableId = "baidu_tbl_" + pageNum + "_" + tIdx;
            List<CanonicalCellDto> cells = new ArrayList<>();
            List<MergedRegionDto> mergedRegions = new ArrayList<>();

            for (BaiduTableCell cell : item.body()) {
                String cellId = tableId + "_r" + cell.rowStart() + "_c" + cell.colStart();
                String text = cell.words() != null ? cell.words() : "";
                boolean isMerged = cell.rowspan() > 1 || cell.colspan() > 1;
                cells.add(new CanonicalCellDto(
                        cellId,
                        cell.rowStart(),
                        cell.colStart(),
                        text,
                        text.trim(),
                        cell.rowspan(),
                        cell.colspan(),
                        false,
                        "",
                        pageNum
                ));

                if (isMerged) {
                    mergedRegions.add(new MergedRegionDto(cellId, cell.rowspan(), cell.colspan()));
                }
            }

            boolean isScore = isLikelyScoreTable(item);
            String effectiveSection = (sourceSection != null && !sourceSection.isBlank())
                    ? sourceSection
                    : (isScore ? "评审办法/详细评审标准" : "招标文件正文");
            canonicalTables.add(new CanonicalTable(
                    tableId,
                    pageNum,
                    effectiveSection,
                    rowCount,
                    colCount,
                    mergedRegions,
                    cells,
                    isScore,
                    "PDF"
            ));
        }

        return List.copyOf(canonicalTables);
    }

    private static List<List<CellPayload>> buildGrid(List<BaiduTableCell> cells) {
        int maxRow = cells.stream().mapToInt(BaiduTableCell::rowEnd).max().orElse(0);
        int maxCol = cells.stream().mapToInt(BaiduTableCell::colEnd).max().orElse(0);
        int rowCount = maxRow + 1;
        int colCount = maxCol + 1;

        CellPayload[][] grid = new CellPayload[rowCount][colCount];

        for (BaiduTableCell c : cells) {
            int rStart = c.rowStart();
            int rEnd = c.rowEnd();
            int cStart = c.colStart();
            int cEnd = c.colEnd();
            int rowspan = c.rowspan();
            int colspan = c.colspan();
            String text = c.words() != null ? c.words() : "";

            if (rStart < rowCount && cStart < colCount) {
                grid[rStart][cStart] = new CellPayload(text, rowspan, colspan, false);
            }

            // 填充跨行列的合并占位格子 (isMergedContinue = true)
            for (int r = rStart; r <= rEnd && r < rowCount; r++) {
                for (int col = cStart; col <= cEnd && col < colCount; col++) {
                    if (r == rStart && col == cStart) {
                        continue;
                    }
                    grid[r][col] = new CellPayload("", rowspan, colspan, true);
                }
            }
        }

        // 补齐可能未被覆盖的空白格子
        List<List<CellPayload>> result = new ArrayList<>(rowCount);
        for (int r = 0; r < rowCount; r++) {
            List<CellPayload> rowList = new ArrayList<>(colCount);
            for (int col = 0; col < colCount; col++) {
                CellPayload p = grid[r][col];
                if (p == null) {
                    p = new CellPayload("", 1, 1, false);
                }
                rowList.add(p);
            }
            result.add(List.copyOf(rowList));
        }
        return List.copyOf(result);
    }

    private static boolean isLikelyScoreTable(BaiduTableItem item) {
        return item.body().stream()
                .anyMatch(cell -> cell.words() != null && SCORE_TABLE_KEYWORDS.stream().anyMatch(kw -> cell.words().contains(kw)));
    }
}
