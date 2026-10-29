package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.CellPayload;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction.TablePayload;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableCell;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableItem;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableWord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BaiduTableOcrTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("单元测试: BaiduTableArtifactAdapter 应正确处理合并单元格并剥离页眉页脚元数据")
    void adapter_shouldHandleMergedCellsAndHeadersCorrectly() {
        // 构造一个包含跨行合并单元格、页眉和页脚的模拟表格响应
        BaiduTableWord header = new BaiduTableWord("第三章评标办法", List.of());
        BaiduTableWord footer = new BaiduTableWord("第1页", List.of());

        // 2行2列网格：(0,0) 跨2行合并 "商务标"，(0,1) "企业业绩 10分"，(1,1) "财务能力 10分"
        BaiduTableCell c00 = new BaiduTableCell(0, 1, 0, 0, "商务标(20分)", List.of());
        BaiduTableCell c01 = new BaiduTableCell(0, 0, 1, 1, "企业业绩 10分", List.of());
        BaiduTableCell c11 = new BaiduTableCell(1, 1, 1, 1, "财务能力 10分", List.of());

        BaiduTableItem tableItem = new BaiduTableItem(List.of(header), List.of(footer), List.of(c00, c01, c11), List.of());
        BaiduTableResponse mockResponse = new BaiduTableResponse(1, List.of(tableItem), 0, "success", 12345L);

        // 1. 转换为 SidecarTableExtraction
        SidecarTableExtraction sidecarResult = BaiduTableArtifactAdapter.toSidecarTableExtraction(mockResponse);
        assertThat(sidecarResult.tables()).hasSize(1);
        TablePayload table = sidecarResult.tables().get(0);
        assertThat(table.isScoreTable()).isTrue();
        assertThat(table.rows()).hasSize(2);

        // 验证第0行
        List<CellPayload> row0 = table.rows().get(0);
        assertThat(row0.get(0).text()).isEqualTo("商务标(20分)");
        assertThat(row0.get(0).rowspan()).isEqualTo(2);
        assertThat(row0.get(0).isMergedContinue()).isFalse();
        assertThat(row0.get(1).text()).isEqualTo("企业业绩 10分");

        // 验证第1行：第0列应为合并占位格 (isMergedContinue = true)
        List<CellPayload> row1 = table.rows().get(1);
        assertThat(row1.get(0).text()).isEmpty();
        assertThat(row1.get(0).rowspan()).isEqualTo(2);
        assertThat(row1.get(0).isMergedContinue()).isTrue();
        assertThat(row1.get(1).text()).isEqualTo("财务能力 10分");

        // 验证 warnings 记录了页眉页脚
        assertThat(sidecarResult.warnings()).anyMatch(w -> w.contains("第三章评标办法"));
        assertThat(sidecarResult.warnings()).anyMatch(w -> w.contains("第1页"));

        // 2. 转换为 DocumentArtifact
        DocumentArtifact docArtifact = BaiduTableArtifactAdapter.toDocumentArtifact(mockResponse, "test-doc-1", 1);
        assertThat(docArtifact.tables()).hasSize(1);
        var canonicalTable = docArtifact.tables().get(0);
        assertThat(canonicalTable.rows()).isEqualTo(2);
        assertThat(canonicalTable.columns()).isEqualTo(2);
        assertThat(canonicalTable.mergedRegions()).hasSize(1);
        assertThat(canonicalTable.mergedRegions().get(0).rowspan()).isEqualTo(2);
    }

    @Test
    @DisplayName("端到端集成测试: 使用真实凭据对全页边框 PDF 样本进行表格提取")
    void client_shouldExtractTablesFromRealPdfFixture() throws Exception {
        BaiduOcrProperties props = new BaiduOcrProperties();
        props.setEnabled(true);
        props.setApiKey("VJ8ZnT3WTnUR433O7QX6sWlY");
        props.setSecretKey("QE4wXv7U774mb5IceRRY3XpI7FsY7bKC");

        BaiduTableOcrClient client = new BaiduTableOcrClient(props, objectMapper);

        // 获取真实的 PDF 测试样本 (支持从根目录或 backend 子目录执行)
        File pdfFile = new File("../document-converter-sidecar/tests/fixtures/page_border_continuation.pdf");
        if (!pdfFile.exists()) {
            pdfFile = new File("document-converter-sidecar/tests/fixtures/page_border_continuation.pdf");
        }
        if (!pdfFile.exists()) {
            pdfFile = new File("../../document-converter-sidecar/tests/fixtures/page_border_continuation.pdf");
        }
        assertThat(pdfFile).exists();
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());

        // 执行第 1 页表格提取
        SidecarTableExtraction page1Result = client.extractSidecarTables(pdfBytes, 1);
        assertThat(page1Result.tables()).isNotEmpty();

        TablePayload tableP1 = page1Result.tables().get(0);
        assertThat(tableP1.isScoreTable()).isTrue();
        assertThat(tableP1.rows()).isNotEmpty();

        // 验证第0行表头
        String headerRowText = tableP1.rows().get(0).stream().map(CellPayload::text).reduce("", (a, b) -> a + " " + b);
        assertThat(headerRowText).contains("评分因素").contains("评分标准");

        // 验证全页边框的页眉被成功分离并记入 warnings，没有污染正文表格
        assertThat(page1Result.warnings()).anyMatch(w -> w.contains("贵州能源集团采购项目招标文件"));
    }

    @Test
    @DisplayName("诊断测试: 使用真实百度OCR识别project1130.pdf中的评分表格，检查缺失的第10/11/12项")
    void client_shouldDiagnoseProject1130ScoringTables() throws Exception {
        File pdfFile = new File("src/test/resources/project1130.pdf");
        if (!pdfFile.exists()) {
            pdfFile = new File("backend/src/test/resources/project1130.pdf");
        }
        assertThat(pdfFile).exists();
        byte[] pdfBytes = Files.readAllBytes(pdfFile.toPath());

        // 1. 扫描页码
        List<Integer> targetPages = new java.util.ArrayList<>();
        try (var doc = org.apache.pdfbox.pdmodel.PDDocument.load(pdfFile)) {
            var stripper = new org.apache.pdfbox.text.PDFTextStripper();
            for (int p = 1; p <= doc.getNumberOfPages(); p++) {
                stripper.setStartPage(p);
                stripper.setEndPage(p);
                String text = stripper.getText(doc);
                if (text.contains("评分") || text.contains("评标") || text.contains("详细评审") || text.contains("价格限制") || text.contains("结算账期")) {
                    System.out.println("Page " + p + " has scoring keywords: " + text.lines().limit(3).toList());
                    targetPages.add(p);
                }
            }
        }
        System.out.println("=== Target scoring pages in project1130: " + targetPages + " ===");

        BaiduOcrProperties props = new BaiduOcrProperties();
        props.setEnabled(true);
        props.setApiKey("VJ8ZnT3WTnUR433O7QX6sWlY");
        props.setSecretKey("QE4wXv7U774mb5IceRRY3XpI7FsY7bKC");

        BaiduTableOcrClient client = new BaiduTableOcrClient(props, objectMapper);

        for (int pageNum : targetPages) {
            System.out.println("\n>>> Calling Baidu OCR for Page " + pageNum + "...");
            var result = client.extractSidecarTables(pdfBytes, pageNum);
            System.out.println("Page " + pageNum + " tables count: " + result.tables().size());
            for (int tIdx = 0; tIdx < result.tables().size(); tIdx++) {
                var table = result.tables().get(tIdx);
                System.out.println("  Table " + tIdx + " isScore=" + table.isScoreTable() + " rows=" + table.rows().size());
                for (int r = 0; r < table.rows().size(); r++) {
                    var row = table.rows().get(r);
                    var rowText = row.stream().map(c -> "[" + c.text() + "]").toList();
                    System.out.println("    Row " + r + ": " + String.join(" | ", rowText));
                }
            }
        }
    }

    @Test
    @DisplayName("单元测试: slicePdfPage 应能精准切出目标单页并将体积大幅缩减")
    void slicePdfPage_shouldExtractOnlyTargetPage() throws Exception {
        // 创建一个包含 3 页的测试 PDF
        byte[] multiPagePdf;
        try (var doc = new org.apache.pdfbox.pdmodel.PDDocument()) {
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            doc.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            var baos = new java.io.ByteArrayOutputStream();
            doc.save(baos);
            multiPagePdf = baos.toByteArray();
        }

        // 切第 2 页
        byte[] sliced = BaiduTableOcrClient.slicePdfPage(multiPagePdf, 2);
        assertThat(sliced).isNotNull();
        assertThat(sliced.length).isGreaterThan(0);

        // 验证切片后的 PDF 只有 1 页
        try (var doc = org.apache.pdfbox.pdmodel.PDDocument.load(sliced)) {
            assertThat(doc.getNumberOfPages()).isEqualTo(1);
        }

        // 验证边界异常安全回退
        assertThat(BaiduTableOcrClient.slicePdfPage(multiPagePdf, 99)).isEqualTo(multiPagePdf);
        assertThat(BaiduTableOcrClient.slicePdfPage(multiPagePdf, 0)).isEqualTo(multiPagePdf);
        assertThat(BaiduTableOcrClient.slicePdfPage(null, 1)).isNull();
    }
}
