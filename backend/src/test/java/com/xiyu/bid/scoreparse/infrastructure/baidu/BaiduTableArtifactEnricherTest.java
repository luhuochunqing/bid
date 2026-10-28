package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduTableArtifactEnricherTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("当百度OCR未启用时，enrichIfEnabled直接原样返回baseline")
    void shouldReturnBaselineWhenDisabled() {
        BaiduOcrProperties props = new BaiduOcrProperties();
        props.setEnabled(false);
        BaiduTableOcrClient client = new BaiduTableOcrClient(props, objectMapper);
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(client);

        ArtifactMeta meta = new ArtifactMeta("doc-1", "sha", "PDF", "sidecar");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(), List.of());

        DocumentArtifact result = enricher.enrichIfEnabled(baseline, new byte[]{1, 2, 3}, "test.pdf");
        assertThat(result).isSameAs(baseline);
    }

    @Test
    @DisplayName("当输入文件不是PDF时，enrichIfEnabled直接返回baseline")
    void shouldReturnBaselineWhenNotPdf() {
        BaiduOcrProperties props = new BaiduOcrProperties();
        props.setEnabled(true);
        BaiduTableOcrClient client = new BaiduTableOcrClient(props, objectMapper);
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(client);

        ArtifactMeta meta = new ArtifactMeta("doc-1", "sha", "DOCX", "sidecar");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(), List.of());

        DocumentArtifact result = enricher.enrichIfEnabled(baseline, new byte[]{1, 2, 3}, "test.docx");
        assertThat(result).isSameAs(baseline);
    }

    @Test
    @DisplayName("针对真实project1130.pdf能够智能探测出评分页及续表页")
    void shouldDetectScoringPagesForProject1130() throws Exception {
        File pdfFile = new File("src/test/resources/project1130.pdf");
        if (!pdfFile.exists()) {
            pdfFile = new File("backend/src/test/resources/project1130.pdf");
        }
        assertThat(pdfFile).exists();
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());

        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        List<Integer> pages = enricher.detectScoringPages(pdfBytes);
        assertThat(pages).contains(13, 14, 15);
    }

    @Test
    @DisplayName("端到端验证: 百度OCR作为Primary Authority主管评分区间，提取完整评分表并替换baseline")
    void shouldEnrichMissingPage14WithRealBaiduOcr() throws Exception {
        File pdfFile = new File("src/test/resources/project1130.pdf");
        if (!pdfFile.exists()) {
            pdfFile = new File("backend/src/test/resources/project1130.pdf");
        }
        assertThat(pdfFile).exists();
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());

        BaiduOcrProperties props = new BaiduOcrProperties();
        props.setEnabled(true);
        props.setApiKey("VJ8ZnT3WTnUR433O7QX6sWlY");
        props.setSecretKey("QE4wXv7U774mb5IceRRY3XpI7FsY7bKC");
        BaiduTableOcrClient client = new BaiduTableOcrClient(props, objectMapper);
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(client);

        // 模拟 Sidecar 提取的 baseline：包含 13 页和 15 页评分表格，但格式脆弱
        ArtifactMeta meta = new ArtifactMeta("doc-1130", "sha1130", "PDF", "pdf-table-v2");
        CanonicalTable t13 = new CanonicalTable("sidecar_13", 13, "第四章 评审办法", 4, 4, List.of(), List.of(), true, "PDF");
        CanonicalTable t15 = new CanonicalTable("sidecar_15", 15, "第四章 评审办法", 3, 4, List.of(), List.of(), true, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t13, t15), List.of());

        DocumentArtifact enriched = enricher.enrichIfEnabled(baseline, pdfBytes, "project1130.pdf");
        assertThat(enriched).isNotNull();

        // 验证必须包含第 14 页的表格，且 section 智能继承邻近页的 "第四章 评审办法"
        CanonicalTable page14Table = enriched.tables().stream()
                .filter(t -> t.sourcePage() != null && t.sourcePage() == 14)
                .findFirst()
                .orElse(null);
        assertThat(page14Table).isNotNull();
        assertThat(page14Table.isScoreTable()).isTrue();
        assertThat(page14Table.sourceSection()).isEqualTo("第四章 评审办法");

        boolean foundPricingRule = page14Table.cells().stream()
                .map(c -> c.rawText() == null ? "" : c.rawText().replaceAll("\\s+", ""))
                .anyMatch(text -> text.contains("销售价格不高于向任何第三方销售价格")
                        && text.contains("结算账期必须在90天及以上")
                        && text.contains("对应推送及上架相关商品得5分"));
        assertThat(foundPricingRule).as("第14页应包含完整的价格优惠三项合一细则").isTrue();

        // 验证所有百度 OCR 提取的表格均满足 CanonicalTableGridFactory 校验
        for (CanonicalTable t : enriched.tables()) {
            if (t.tableId().startsWith("baidu_tbl_")) {
                CanonicalTableGrid grid = CanonicalTableGridFactory.fromSidecarTable(t);
                assertThat(grid.validation().valid()).as("Baidu table " + t.tableId() + " should be structurally valid").isTrue();
            }
        }
    }

    @Test
    @DisplayName("determinePagesToScan测试: 在Primary Authority模式下，接管评分核心区间的全部页面")
    void shouldDetermineAllPagesInScoringRangeAsPrimaryAuthority() {
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        ArtifactMeta meta = new ArtifactMeta("doc-1", "sha", "PDF", "sidecar");
        CanonicalTable t13 = new CanonicalTable("t13", 13, "第四章 评审办法", 1, 1, List.of(), List.of(), true, "PDF");
        CanonicalTable t15 = new CanonicalTable("t15", 15, "第四章 评审办法", 1, 1, List.of(), List.of(), true, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t13, t15), List.of());

        List<Integer> candidates = List.of(3, 4, 9, 10, 13, 14, 15, 16);
        List<Integer> target = enricher.determinePagesToScan(baseline, candidates);

        // 核心评分区间为 [13, 16]，包含 13, 14, 15, 16，不包含远端非评分页 3, 4, 9, 10
        assertThat(target).contains(13, 14, 15, 16);
        assertThat(target).doesNotContain(3, 4, 9, 10);
    }

    @Test
    @DisplayName("跨页续行测试: 自动将下一页首行续行的logicalRowId衔接到前一页最后一行")
    void shouldLinkCrossPageContinuationLogicalRow() {
        // Table 1 on page 14: row 0 (header), row 1 (9.1 item)
        CanonicalCellDto c14_0_0 = new CanonicalCellDto("c14_0_0", 0, 0, "序号", "序号", 1, 1, false, "path", 14, "tbl14-r0");
        CanonicalCellDto c14_0_1 = new CanonicalCellDto("c14_0_1", 0, 1, "内容", "内容", 1, 1, false, "path", 14, "tbl14-r0");
        CanonicalCellDto c14_1_0 = new CanonicalCellDto("c14_1_0", 1, 0, "9.1", "9.1", 1, 1, false, "path", 14, "tbl14-r1");
        CanonicalCellDto c14_1_1 = new CanonicalCellDto("c14_1_1", 1, 1, "技术要求前半句", "技术要求前半句", 1, 1, false, "path", 14, "tbl14-r1");
        CanonicalTable table14 = new CanonicalTable("tbl14", 14, "第四章", 2, 2, List.of(),
                List.of(c14_0_0, c14_0_1, c14_1_0, c14_1_1), true, "PDF");

        // Table 2 on page 15: row 0 (continuation of 9.1: col 0 empty, col 1 has text), row 1 (9.2 item)
        CanonicalCellDto c15_0_0 = new CanonicalCellDto("c15_0_0", 0, 0, "", "", 1, 1, false, "path", 15, "tbl15-r0");
        CanonicalCellDto c15_0_1 = new CanonicalCellDto("c15_0_1", 0, 1, "显示视频。）", "显示视频。）", 1, 1, false, "path", 15, "tbl15-r0");
        CanonicalCellDto c15_1_0 = new CanonicalCellDto("c15_1_0", 1, 0, "9.2", "9.2", 1, 1, false, "path", 15, "tbl15-r1");
        CanonicalCellDto c15_1_1 = new CanonicalCellDto("c15_1_1", 1, 1, "其他要求", "其他要求", 1, 1, false, "path", 15, "tbl15-r1");
        CanonicalTable table15 = new CanonicalTable("tbl15", 15, "第四章", 2, 2, List.of(),
                List.of(c15_0_0, c15_0_1, c15_1_0, c15_1_1), true, "PDF");

        List<CanonicalTable> linked = BaiduCrossPageTableLinker.linkCrossPageLogicalRows(List.of(table14, table15));
        assertThat(linked).hasSize(2);

        CanonicalTable linkedTable15 = linked.get(1);
        CanonicalCellDto linkedContCell = linkedTable15.cells().stream()
                .filter(c -> c.row() == 0 && c.column() == 1)
                .findFirst().orElseThrow();

        // 验证：第 15 页第 0 行续行的 logicalRowId 必须无缝衔接到第 14 页的 "tbl14-r1"
        assertThat(linkedContCell.logicalRowId()).isEqualTo("tbl14-r1");

        // 验证：第 15 页第 1 行独立评分项不受影响
        CanonicalCellDto nextCell = linkedTable15.cells().stream()
                .filter(c -> c.row() == 1 && c.column() == 0)
                .findFirst().orElseThrow();
        assertThat(nextCell.logicalRowId()).isEqualTo("tbl15-r1");
    }
}
