package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.DocumentContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SidecarStreamDocumentAdapter 降级与安全守卫测试")
class SidecarStreamDocumentAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    // 使用无效的 sidecarUrl 强制触发 fallback 降级
    private final SidecarStreamDocumentAdapter adapter = new SidecarStreamDocumentAdapter(
            objectMapper, "http://127.0.0.1:59999", "", 1
    );

    @Test
    @DisplayName("降级时：纯文本小文件（<=5MB）正常读取内容并生成哈希")
    void fallback_smallPlainText_readsContent(@TempDir Path tempDir) throws IOException {
        Path txtFile = tempDir.resolve("sample.txt");
        Files.writeString(txtFile, "纯文本招标条款内容");

        DocumentContent content = adapter.convertDocument(txtFile, "sample.txt");

        assertThat(content).isNotNull();
        assertThat(content.fileName()).isEqualTo("sample.txt");
        assertThat(content.fileHash()).isNotEmpty();
        assertThat(content.fullText()).isEqualTo("纯文本招标条款内容");
    }

    @Test
    @DisplayName("降级时：二进制 docx 文件不执行本地 readString，仅计算哈希并返回空文本")
    void fallback_binaryDocx_skipsReadString(@TempDir Path tempDir) throws IOException {
        Path docxFile = tempDir.resolve("huge_bid.docx");
        // 写入模拟二进制内容（包含非 UTF-8 字符）
        byte[] dummyBytes = new byte[]{0x50, 0x4B, 0x03, 0x04, (byte) 0xFF, (byte) 0xFE};
        Files.write(docxFile, dummyBytes);

        DocumentContent content = adapter.convertDocument(docxFile, "huge_bid.docx");

        assertThat(content).isNotNull();
        assertThat(content.fileName()).isEqualTo("huge_bid.docx");
        assertThat(content.fileHash()).isNotEmpty();
        // 必须返回空文本，不能把二进制当字符串读
        assertThat(content.fullText()).isEmpty();
    }

    @Test
    @DisplayName("降级时：超大纯文本文件（>5MB）跳过本地读取，防止 DirectBuffer OOM")
    void fallback_largeFile_skipsReadString(@TempDir Path tempDir) throws IOException {
        Path largeTxtFile = tempDir.resolve("huge.txt");
        // 构造 6MB 文件
        byte[] largeBytes = new byte[6 * 1024 * 1024];
        Files.write(largeTxtFile, largeBytes);

        DocumentContent content = adapter.convertDocument(largeTxtFile, "huge.txt");

        assertThat(content).isNotNull();
        assertThat(content.fileHash()).isNotEmpty();
        assertThat(content.fullText()).isEmpty();
    }

    @Test
    @DisplayName("大 docx 文件在发送 Sidecar 时能正常通过 DocxMediaFilter 流式过滤并计算原文件哈希")
    void convertDocument_largeDocx_filtersMediaCorrectly(@TempDir Path tempDir) throws IOException {
        Path largeDocx = tempDir.resolve("huge_with_media.docx");
        // 创建 21MB 的 docx 文件（模拟大体积）
        byte[] dummyDocx = new byte[21 * 1024 * 1024];
        dummyDocx[0] = 0x50;
        dummyDocx[1] = 0x4B;
        dummyDocx[2] = 0x03;
        dummyDocx[3] = 0x04;
        Files.write(largeDocx, dummyDocx);

        DocumentContent content = adapter.convertDocument(largeDocx, "huge_with_media.docx");

        assertThat(content).isNotNull();
        assertThat(content.fileName()).isEqualTo("huge_with_media.docx");
        assertThat(content.fileHash()).isNotEmpty();
    }

    @Nested
    @DisplayName("大文件超时分档（1373 根治：428MB 标书 60s 内转不完）")
    class LargeFileTimeoutTests {

        @Test
        @DisplayName("小文件走基准档")
        void smallFile_usesBaseTimeout() {
            assertThat(adapter.resolveTimeoutSeconds(10 * 1024 * 1024)).isEqualTo(1);
        }

        @Test
        @DisplayName("阈值边界（恰好 50MB）仍走基准档")
        void thresholdBoundary_usesBaseTimeout() {
            assertThat(adapter.resolveTimeoutSeconds(50 * 1024 * 1024)).isEqualTo(1);
        }

        @Test
        @DisplayName("超阈值文件走大文件档（基准档 × 5）")
        void largeFile_usesLargeTimeout() {
            assertThat(adapter.resolveTimeoutSeconds(51 * 1024 * 1024)).isEqualTo(5);
            assertThat(adapter.resolveTimeoutSeconds(448688453L)).isEqualTo(5);
        }

        @Test
        @DisplayName("未知大小（0/负数）回落基准档")
        void unknownSize_fallsBackToBaseTimeout() {
            assertThat(adapter.resolveTimeoutSeconds(0)).isEqualTo(1);
            assertThat(adapter.resolveTimeoutSeconds(-1)).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Sidecar 响应解析：结构化 pages 与虚拟分页兜底")
    class PayloadParsingTests {

        private com.fasterxml.jackson.databind.JsonNode payload(final String json) throws IOException {
            return objectMapper.readTree(json);
        }

        @Test
        @DisplayName("响应含结构化 pages：按物理页构建，不再虚拟切块")
        void payloadWithPages_buildsPhysicalPages() throws IOException {
            com.fasterxml.jackson.databind.JsonNode root = payload("""
                    {"markdown": "第一页内容\\f第二页内容",
                     "pages": [{"pageNumber": 1, "text": "第一页内容"},
                               {"pageNumber": 2, "text": "第二页内容"}]}
                    """);

            DocumentContent doc = adapter.documentFromSidecarPayload("bid.pdf", "hash", root);

            assertThat(doc.pages()).hasSize(2);
            assertThat(doc.pages().get(0).pageNumber()).isEqualTo(1);
            assertThat(doc.pages().get(1).text()).isEqualTo("第二页内容");
        }

        @Test
        @DisplayName("响应无 pages 且文本无 \\f：超阈值长文本虚拟分页，页数恢复有效")
        void payloadWithoutPages_longText_fallsBackToVirtualPages() throws IOException {
            String hugeMarkdown = "投标文件正文段落，用于模拟 markitdown 无分页符输出。".repeat(300);
            com.fasterxml.jackson.databind.JsonNode root = payload(
                    "{\"markdown\": \"" + hugeMarkdown + "\"}");

            DocumentContent doc = adapter.documentFromSidecarPayload("bid.docx", "hash", root);

            assertThat(doc.pages().size()).as("长文本应被虚拟分页为多块").isGreaterThanOrEqualTo(2);
            assertThat(doc.pages().get(0).pageNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("响应无 pages 且文本无 \\f：短文本保持单页（回归保护）")
        void payloadWithoutPages_shortText_singlePage() throws IOException {
            com.fasterxml.jackson.databind.JsonNode root = payload(
                    "{\"markdown\": \"短文档内容，无需切块。\"}");

            DocumentContent doc = adapter.documentFromSidecarPayload("bid.docx", "hash", root);

            assertThat(doc.pages()).hasSize(1);
        }

        @Test
        @DisplayName("响应无 pages 但文本含 \\f：按物理分页符切分（回归保护）")
        void payloadWithoutPages_formFeed_physicalSplit() throws IOException {
            com.fasterxml.jackson.databind.JsonNode root = payload(
                    "{\"markdown\": \"甲页内容\\f乙页内容\\f丙页内容\"}");

            DocumentContent doc = adapter.documentFromSidecarPayload("bid.pdf", "hash", root);

            assertThat(doc.pages()).hasSize(3);
        }
    }
}

