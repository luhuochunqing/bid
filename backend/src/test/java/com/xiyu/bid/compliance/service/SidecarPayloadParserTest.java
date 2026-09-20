// Input: Sidecar /convert 响应负载（pages/分页符/虚拟分页/警告）
// Output: 验证负载解析与降级读取（pages 优先、\f 切分、虚拟分页、warnings 提取）
// Pos: compliance/service/ - Sidecar 负载解析单测（spec 066 T011 拆分）
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
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

@DisplayName("SidecarPayloadParser 负载解析与降级读取")
class SidecarPayloadParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private JsonNode payload(final String json) throws IOException {
        return objectMapper.readTree(json);
    }

    @Nested
    @DisplayName("负载 → DocumentContent")
    class PayloadParsingTests {

        @Test
        @DisplayName("响应含结构化 pages：按物理页构建，不再虚拟切块")
        void payloadWithPages_buildsPhysicalPages() throws IOException {
            JsonNode root = payload("""
                    {"markdown": "第一页内容\\f第二页内容",
                     "pages": [{"pageNumber": 1, "text": "第一页内容"},
                               {"pageNumber": 2, "text": "第二页内容"}]}
                    """);

            DocumentContent doc = SidecarPayloadParser.documentFromSidecarPayload("bid.pdf", "hash", root);

            assertThat(doc.pages()).hasSize(2);
            assertThat(doc.pages().get(0).pageNumber()).isEqualTo(1);
            assertThat(doc.pages().get(1).text()).isEqualTo("第二页内容");
        }

        @Test
        @DisplayName("响应无 pages 且文本无 \\f：超阈值长文本虚拟分页，页数恢复有效")
        void payloadWithoutPages_longText_fallsBackToVirtualPages() throws IOException {
            String hugeMarkdown = "投标文件正文段落，用于模拟 markitdown 无分页符输出。".repeat(300);
            JsonNode root = payload("{\"markdown\": \"" + hugeMarkdown + "\"}");

            DocumentContent doc = SidecarPayloadParser.documentFromSidecarPayload("bid.docx", "hash", root);

            assertThat(doc.pages().size()).as("长文本应被虚拟分页为多块").isGreaterThanOrEqualTo(2);
            assertThat(doc.pages().get(0).pageNumber()).isEqualTo(1);
        }

        @Test
        @DisplayName("响应无 pages 且文本无 \\f：短文本保持单页（回归保护）")
        void payloadWithoutPages_shortText_singlePage() throws IOException {
            JsonNode root = payload("{\"markdown\": \"短文档内容，无需切块。\"}");

            DocumentContent doc = SidecarPayloadParser.documentFromSidecarPayload("bid.docx", "hash", root);

            assertThat(doc.pages()).hasSize(1);
        }

        @Test
        @DisplayName("响应无 pages 但文本含 \\f：按物理分页符切分（回归保护）")
        void payloadWithoutPages_formFeed_physicalSplit() throws IOException {
            JsonNode root = payload("{\"markdown\": \"甲页内容\\f乙页内容\\f丙页内容\"}");

            DocumentContent doc = SidecarPayloadParser.documentFromSidecarPayload("bid.pdf", "hash", root);

            assertThat(doc.pages()).hasSize(3);
        }

        @Test
        @DisplayName("warnings 提取：数组取值，非数组/缺失返回空列表")
        void warnings_extractedFromArrayOnly() throws IOException {
            assertThat(SidecarPayloadParser.warningsOf(payload("{\"warnings\": [\"low_text_density\", \"\"]}")))
                    .containsExactly("low_text_density");
            assertThat(SidecarPayloadParser.warningsOf(payload("{\"warnings\": \"oops\"}"))).isEmpty();
            assertThat(SidecarPayloadParser.warningsOf(payload("{}"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("本地纯文本降级读取")
    class LocalFallbackTests {

        @Test
        @DisplayName("纯文本小文件可读且内容完整")
        void smallPlainText_readable(@TempDir Path tempDir) throws IOException {
            Path txt = tempDir.resolve("notes.txt");
            Files.writeString(txt, "纯文本招标条款内容");

            assertThat(SidecarPayloadParser.isSmallPlainTextFile("notes.txt", txt)).isTrue();
            assertThat(SidecarPayloadParser.readLocalPlainText(txt)).isEqualTo("纯文本招标条款内容");
        }

        @Test
        @DisplayName("二进制/大文件不进入本地降级")
        void binaryOrLarge_notEligible(@TempDir Path tempDir) throws IOException {
            Path docx = tempDir.resolve("bid.docx");
            Files.write(docx, new byte[]{0x50, 0x4B, 0x03, 0x04});

            assertThat(SidecarPayloadParser.isSmallPlainTextFile("bid.docx", docx)).isFalse();
        }

        @Test
        @DisplayName("buildDocumentContent：空文本返回空文档，短文本单页")
        void buildDocumentContent_blankAndShort(@TempDir Path tempDir) {
            assertThat(SidecarPayloadParser.buildDocumentContent("a.txt", "h", "  ").fullText()).isEmpty();
            assertThat(SidecarPayloadParser.buildDocumentContent("a.txt", "h", "短文本").pages()).hasSize(1);
        }

        @Test
        @DisplayName("computeSha256：存在文件产出 64 位十六进制，缺失文件返回空串")
        void computeSha256_behaviour(@TempDir Path tempDir) throws IOException {
            Path txt = tempDir.resolve("hash.txt");
            Files.writeString(txt, "hash-me");

            assertThat(SidecarPayloadParser.computeSha256(txt)).hasSize(64).matches("[0-9a-f]+");
            assertThat(SidecarPayloadParser.computeSha256(tempDir.resolve("missing.txt"))).isEmpty();
        }

        @Test
        @DisplayName("readLocalPlainText 失败返回空串（调用方保持失败分类）")
        void readLocalPlainText_missingFile_empty(@TempDir Path tempDir) {
            assertThat(SidecarPayloadParser.readLocalPlainText(tempDir.resolve("missing.txt"))).isEmpty();
        }
    }
}
