package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.CellPayload;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.TablePayload;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
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
            if (item.body().isEmpty()) continue;

            List<List<CellPayload>> rows = BaiduTableGridBuilder.buildSimplePayloadGrid(item.body());
            boolean isScore = isLikelyScoreTable(item);
            tables.add(new TablePayload(i, rows, isScore));

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
     * <p>构建严格符合 2D 拓扑结构要求的单元格矩阵（完整覆盖所有位置、合并占位格标记、源路径溯源、逻辑行关联）。
     */
    public static List<CanonicalTable> toCanonicalTables(BaiduTableResponse response, int pageNum, String sourceSection) {
        if (response == null || !response.isSuccess() || response.tablesResult().isEmpty()) {
            return List.of();
        }

        List<CanonicalTable> canonicalTables = new ArrayList<>();
        for (int tIdx = 0; tIdx < response.tablesResult().size(); tIdx++) {
            BaiduTableItem item = response.tablesResult().get(tIdx);
            if (item.body().isEmpty()) continue;

            String tableId = "baidu_tbl_" + pageNum + "_" + tIdx;
            var gridResult = BaiduTableGridBuilder.buildCanonicalGrid(item.body(), tableId, pageNum);

            boolean isScore = isLikelyScoreTable(item);
            String effectiveSection = !isScore
                    ? "招标文件正文"
                    : (sourceSection != null && !sourceSection.isBlank() ? sourceSection : "评审办法/详细评审标准");
            canonicalTables.add(new CanonicalTable(
                    tableId,
                    pageNum,
                    effectiveSection,
                    gridResult.rowCount(),
                    gridResult.colCount(),
                    gridResult.mergedRegions(),
                    gridResult.cells(),
                    isScore,
                    "PDF"
            ));
        }

        return List.copyOf(canonicalTables);
    }

    private static boolean isLikelyScoreTable(BaiduTableItem item) {
        boolean hasKeyword = item.body().stream()
                .anyMatch(cell -> cell.words() != null && SCORE_TABLE_KEYWORDS.stream().anyMatch(kw -> cell.words().contains(kw)));
        boolean hasScoreValue = item.body().stream()
                .anyMatch(cell -> cell.words() != null && (cell.words().contains("分") || cell.words().contains("%")
                        || cell.words().contains("％") || cell.words().contains("权重")));
        return hasKeyword && hasScoreValue;
    }
}
