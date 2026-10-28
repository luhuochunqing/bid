package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.ArtifactMeta;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
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
    @DisplayName("端到端验证: 当baseline缺失第14页表格时，百度OCR能够精确补齐第14页表格并包含三项完整细则")
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

        // 模拟 Sidecar 提取的 baseline：已有第 13 页和第 15 页评分表格，独缺第 14 页
        ArtifactMeta meta = new ArtifactMeta("doc-1130", "sha1130", "PDF", "pdf-table-v2");
        CanonicalTable t13 = new CanonicalTable("sidecar_13", 13, "第四章 评审办法", 4, 4, List.of(), List.of(), true, "PDF");
        CanonicalTable t15 = new CanonicalTable("sidecar_15", 15, "第四章 评审办法", 3, 4, List.of(), List.of(), true, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t13, t15), List.of());

        DocumentArtifact enriched = enricher.enrichIfEnabled(baseline, pdfBytes, "project1130.pdf");
        assertThat(enriched).isNotNull();
        // 原有 baseline 的 13 和 15 页完整保留，第 14 页由百度 OCR 补齐
        assertThat(enriched.tables()).hasSize(3);

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
    }

    @Test
    @DisplayName("determinePagesToScan测试: 当baseline已有评分表时只精确补齐缺失页，绝不重复调用")
    void shouldDetermineOnlyMissingPages() {
        BaiduTableArtifactEnricher enricher = new BaiduTableArtifactEnricher(null);
        ArtifactMeta meta = new ArtifactMeta("doc-1", "sha", "PDF", "sidecar");
        CanonicalTable t13 = new CanonicalTable("t13", 13, "第四章 评审办法", 1, 1, List.of(), List.of(), true, "PDF");
        CanonicalTable t15 = new CanonicalTable("t15", 15, "第四章 评审办法", 1, 1, List.of(), List.of(), true, "PDF");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(t13, t15), List.of());

        List<Integer> candidates = List.of(3, 4, 9, 10, 13, 14, 15, 16);
        List<Integer> target = enricher.determinePagesToScan(baseline, candidates);

        // 已有 13 和 15 页评分表，目标扫描页应排除已有的 13 和 15，包含缺失的 14
        assertThat(target).doesNotContain(13, 15);
        assertThat(target).contains(14);
    }
}
