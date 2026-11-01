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
import java.util.ArrayList;
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

        if (enriched == baseline) {
            org.junit.jupiter.api.Assumptions.abort("百度 OCR 未返回表格（公有云免费额度已耗尽或被限流），跳过外部联调网络断言");
        }

        // 验证第 13、14、15 页的跨页评分表已合并为同一张 4 列评分表，且包含第 14 页与第 15 页单元格
        List<CanonicalTable> scoreTables = enriched.tables().stream()
                .filter(t -> Boolean.TRUE.equals(t.isScoreTable()))
                .toList();
        assertThat(scoreTables).hasSize(1);
        CanonicalTable mergedScoreTable = scoreTables.get(0);
        assertThat(mergedScoreTable.sourceSection()).isEqualTo("第四章 评审办法");
        assertThat(mergedScoreTable.columns()).isEqualTo(4);
        assertThat(mergedScoreTable.cells().stream().map(CanonicalCellDto::sourcePage).distinct().toList())
                .contains(13, 14, 15);

        boolean foundPricingRule = mergedScoreTable.cells().stream()
                .filter(c -> Integer.valueOf(14).equals(c.sourcePage()))
                .map(c -> c.rawText() == null ? "" : c.rawText().replaceAll("\\s+", ""))
                .anyMatch(text -> text.contains("销售价格不高于向任何第三方销售价格")
                        && text.contains("结算账期必须在90天及以上")
                        && text.contains("对应推送及上架相关商品得5分"));
        assertThat(foundPricingRule).as("合并后的评分表应包含第14页完整的价格优惠三项合一细则").isTrue();

        boolean foundPage15ContinuationLinked = mergedScoreTable.cells().stream()
                .filter(c -> Integer.valueOf(15).equals(c.sourcePage()))
                .anyMatch(c -> c.rawText() != null && c.rawText().contains("人员培训")
                        && mergedScoreTable.cells().stream()
                                .anyMatch(prev -> Integer.valueOf(14).equals(prev.sourcePage())
                                        && prev.rawText() != null && prev.rawText().contains("服务响应")
                                        && prev.logicalRowId().equals(c.logicalRowId())));
        assertThat(foundPage15ContinuationLinked).as("第15页首行续行应与第14页末行第9项共享同一logicalRowId").isTrue();

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
    @DisplayName("回归: Sidecar误标续表页isScoreTable=false时，Baidu OCR仍应覆盖该页（project1130 page15场景）")
    void shouldScanContinuationPageEvenWhenSidecarMisclassifiedAsNonScore() {
        // 模拟 project1130 真实场景：
        // - Sidecar 仅在第13页有 isScoreTable=true（page 15 被误标为 false）
        // - candidatePages = [13, 14, 15, 16, 26]（PDFBox关键词检测到的候选页）
        // - 预期：determinePagesToScan 必须包含 page 15，不能因 baseline 无 isScoreTable=true 的15页就截止于14
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        ArtifactMeta meta = new ArtifactMeta("doc-1130", "sha", "PDF", "sidecar");
        // 只有 page 13 被 Sidecar 标记为评分表，page 15 被误标为 false（续表首行无表头）
        CanonicalTable t13 = new CanonicalTable("p13-t0013", 13, "第四章 评审办法", 6, 5, List.of(), List.of(), true, "PDF");
        CanonicalTable t15 = new CanonicalTable("p15-t0014", 15, "第四章 评审办法", 2, 2, List.of(), List.of(), false, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t13, t15), List.of());

        List<Integer> candidates = List.of(2, 3, 7, 8, 9, 10, 13, 14, 15, 16, 26);
        List<Integer> target = enricher.determinePagesToScan(baseline, candidates);

        // 关键断言：page 15 必须被覆盖（不因 isScoreTable=false 而截止于 page 14）
        assertThat(target).contains(13, 14, 15, 16);
        // 远端非评分页不应纳入
        assertThat(target).doesNotContain(2, 3, 7, 8, 9, 10, 26);
    }

    @Test
    @DisplayName("回归(Project 35): Sidecar漏标首页(P39)为非评分表时，连续前序候选页必须纳入扫描范围，禁止向前硬截断")
    void shouldIncludePrecedingContiguousCandidatePagesWhenBaselineScoreStartsLater() {
        // 模拟 Project 35 真实场景：
        // - 技术标评分表位于 P39，但 Sidecar 漏标（isScoreTable 为 false 或 null）
        // - 商务标评分表位于 P40、P41，Sidecar 标为 true
        // - candidatePages 检测到 P39~P42（以及远端目录页 P3, P4, P9, P10）
        // - 预期：determinePagesToScan 必须包含 P39，不得因 minScorePage=40 而把连续前序页 P39 丢弃
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        ArtifactMeta meta = new ArtifactMeta("doc-35", "sha-35", "PDF", "sidecar");
        CanonicalTable t40 = new CanonicalTable("p40-t1", 40, "第四章 评审办法", 5, 4, List.of(), List.of(), true, "PDF");
        CanonicalTable t41 = new CanonicalTable("p41-t2", 41, "第四章 评审办法", 4, 4, List.of(), List.of(), true, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t40, t41), List.of());

        List<Integer> candidates = List.of(3, 4, 9, 10, 39, 40, 41, 42);
        List<Integer> target = enricher.determinePagesToScan(baseline, candidates);

        // 关键断言：连续前序候选页 P39 必须被纳入扫描
        assertThat(target).contains(39, 40, 41, 42);
        // 远端目录候选页不应纳入
        assertThat(target).doesNotContain(3, 4, 9, 10);
    }

    @Test
    @DisplayName("回归: 多个分散评分区间（如 [20,21] 与 [40,41]）必须全部纳入扫描，严禁单选一个簇")
    void shouldIncludeAllDisjointScoringClustersWhenMultipleRangesExist() {
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        ArtifactMeta meta = new ArtifactMeta("doc-multi-range", "sha", "PDF", "sidecar");

        // 模拟技术标在 P20、P21，商务标在 P40、P41
        CanonicalTable t20 = new CanonicalTable("t20", 20, "第四章 评分标准", 5, 4, List.of(), List.of(), true, "PDF");
        CanonicalTable t21 = new CanonicalTable("t21", 21, "第四章 评分标准", 5, 4, List.of(), List.of(), true, "PDF");
        CanonicalTable t40 = new CanonicalTable("t40", 40, "第四章 商务评分", 5, 4, List.of(), List.of(), true, "PDF");
        CanonicalTable t41 = new CanonicalTable("t41", 41, "第四章 商务评分", 5, 4, List.of(), List.of(), true, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t20, t21, t40, t41), List.of());

        // 候选页包含两个连通区间及其紧邻上下文，以及目录噪音 (P3, P4)
        List<Integer> candidates = List.of(3, 4, 19, 20, 21, 22, 39, 40, 41, 42);
        List<Integer> target = enricher.determinePagesToScan(baseline, candidates);

        // 必须同时覆盖两个评分区间的全量页面
        assertThat(target).contains(19, 20, 21, 22, 39, 40, 41, 42);
        // 远端目录不应纳入
        assertThat(target).doesNotContain(3, 4);
    }

    @Test
    @DisplayName("回归: 连续 15 页长跨页评分大表全量覆盖，严禁被 10 页上限截断，绝不漏掉评分页")
    void shouldNotTruncateContinuous15PageScoringTable() {
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        ArtifactMeta meta = new ArtifactMeta("doc-15-pages", "sha", "PDF", "sidecar");

        // 构造连续 15 页的评分表 (P20 到 P34)
        List<CanonicalTable> tables = new ArrayList<>();
        List<Integer> expectedPages = new ArrayList<>();
        for (int p = 20; p <= 34; p++) {
            tables.add(new CanonicalTable("t" + p, p, "第四章 详细评审", 5, 4, List.of(), List.of(), true, "PDF"));
            expectedPages.add(p);
        }
        DocumentArtifact baseline = new DocumentArtifact(meta, tables, List.of());

        // 候选页包含全部 15 页及目录页
        List<Integer> candidates = new ArrayList<>(List.of(3, 4));
        candidates.addAll(expectedPages);

        List<Integer> target = enricher.determinePagesToScan(baseline, candidates);

        // 严禁截断为 10 页：全部 15 页评分页必须 100% 完整保留
        assertThat(target).containsAll(expectedPages);
        assertThat(target).doesNotContain(3, 4);
        assertThat(target).hasSize(15);
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
