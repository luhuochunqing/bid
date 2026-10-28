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

        // 模拟 Sidecar 漏掉第 14 页表格的 baseline
        ArtifactMeta meta = new ArtifactMeta("doc-1130", "sha1130", "PDF", "pdf-table-v2");
        DocumentArtifact baseline = new DocumentArtifact(meta, List.of(), List.of());

        DocumentArtifact enriched = enricher.enrichIfEnabled(baseline, pdfBytes, "project1130.pdf");
        assertThat(enriched).isNotNull();
        assertThat(enriched.tables()).isNotEmpty();

        // 验证必须包含第 14 页的表格
        CanonicalTable page14Table = enriched.tables().stream()
                .filter(t -> t.sourcePage() != null && t.sourcePage() == 14)
                .findFirst()
                .orElse(null);
        assertThat(page14Table).isNotNull();
        assertThat(page14Table.isScoreTable()).isTrue();
        assertThat(page14Table.sourceSection()).contains("评审");

        // 验证第 14 页表格中包含困扰了 24 小时的那条关键单元格（价格优惠20分：价格承诺10分、账期5分、推送5分）
        System.out.println("=== Page 14 table cells count: " + page14Table.cells().size() + " ===");
        for (var c : page14Table.cells()) {
            System.out.println("Cell [" + c.row() + "," + c.column() + "]: " + c.rawText());
        }

        boolean foundPricingRule = page14Table.cells().stream()
                .map(c -> c.rawText() == null ? "" : c.rawText().replaceAll("\\s+", ""))
                .anyMatch(text -> text.contains("销售价格不高于向任何第三方销售价格")
                        && text.contains("结算账期必须在90天及以上")
                        && text.contains("对应推送及上架相关商品得5分"));
        assertThat(foundPricingRule).as("第14页应包含完整的价格优惠三项合一细则").isTrue();
    }
}
