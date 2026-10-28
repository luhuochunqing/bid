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
}
