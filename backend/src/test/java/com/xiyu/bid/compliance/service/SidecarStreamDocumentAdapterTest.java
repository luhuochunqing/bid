// Input: 本地 stub Sidecar（状态码/负载/延迟）+ 临时文件
// Output: 验证解析结果分类（OK/TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE）与本地降级
// Pos: compliance/service/ - Sidecar 适配器分类契约单测（spec 066 T017/T023）
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xiyu.bid.compliance.domain.SidecarParseOutcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SidecarStreamDocumentAdapter 解析结果分类（spec 066）")
class SidecarStreamDocumentAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    /** 启动 stub Sidecar：drain 请求体 → 可选延迟 → 返回固定状态码与负载. */
    private String startStub(final int status, final String body, final long delayMillis) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/convert", exchange -> {
            try {
                exchange.getRequestBody().readAllBytes();
                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }
                byte[] bytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
                exchange.sendResponseHeaders(status, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            } catch (Exception ignored) {
                // 客户端提前断开（超时用例）时静默退出
            } finally {
                exchange.close();
            }
        });
        server.start();
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String closedPortUrl() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        }
    }

    private static SidecarStreamDocumentAdapter adapterFor(final String url, final long baseSeconds) {
        return new SidecarStreamDocumentAdapter(OBJECT_MAPPER, url, "", baseSeconds);
    }

    private static Path writeFile(final Path dir, final String name, final String content) throws IOException {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Nested
    @DisplayName("成功与空文本分类")
    class SuccessAndEmptyTests {

        @Test
        @DisplayName("200 + 正文与 pages → OK，内容可消费")
        void okWithContent(@TempDir Path tempDir) throws IOException {
            String url = startStub(200, """
                    {"markdown": "真实投标正文", "pages": [{"pageNumber": 1, "text": "真实投标正文"}]}
                    """, 0);
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.OK);
            assertThat(outcome.content().fullText()).isEqualTo("真实投标正文");
            assertThat(outcome.content().pages()).hasSize(1);
        }

        @Test
        @DisplayName("200 + 短文本无 pages → OK（合法短文档不得误判无文本层）")
        void okShortText(@TempDir Path tempDir) throws IOException {
            String url = startStub(200, "{\"markdown\": \"短文档内容\"}", 0);
            Path file = writeFile(tempDir, "bid.docx", "docx-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "bid.docx");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.OK);
            assertThat(outcome.content().pages()).hasSize(1);
        }

        @Test
        @DisplayName("200 + 空 markdown 与空 pages + low_text_density → EMPTY_TEXT（扫描件形态）")
        void emptyTextWithWarning(@TempDir Path tempDir) throws IOException {
            String url = startStub(200, """
                    {"markdown": "", "pages": [], "warnings": ["low_text_density"]}
                    """, 0);
            Path file = writeFile(tempDir, "scan.pdf", "scan-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "scan.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.EMPTY_TEXT);
            assertThat(outcome.detail()).contains("low_text_density");
            assertThat(outcome.warnings()).containsExactly("low_text_density");
            assertThat(outcome.content().fullText()).isEmpty();
            assertThat(outcome.retrySuggested()).isFalse();
        }

        @Test
        @DisplayName("200 + 空 markdown 但 pages 全空白 → EMPTY_TEXT（pages 判据优先于 warnings）")
        void emptyTextWithBlankPages(@TempDir Path tempDir) throws IOException {
            String url = startStub(200, """
                    {"markdown": "", "pages": [{"pageNumber": 1, "text": "   "}]}
                    """, 0);
            Path file = writeFile(tempDir, "scan.pdf", "scan-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "scan.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.EMPTY_TEXT);
        }

        @Test
        @DisplayName("200 + 非法 JSON → SERVICE_ERROR（协议异常，不误报文件损坏）")
        void invalidJson_serviceError(@TempDir Path tempDir) throws IOException {
            String url = startStub(200, "not-a-json", 0);
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.SERVICE_ERROR);
            assertThat(outcome.detail()).contains("响应格式异常");
        }
    }

    @Nested
    @DisplayName("失败分类边界")
    class FailureClassificationTests {

        @Test
        @DisplayName("读超时（服务端慢于上限）→ TIMEOUT，可重试")
        void readTimeout(@TempDir Path tempDir) throws IOException {
            String url = startStub(200, "{\"markdown\": \"late\"}", 2500);
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 1).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.TIMEOUT);
            assertThat(outcome.detail()).contains("等待上限");
            assertThat(outcome.retrySuggested()).isTrue();
        }

        @Test
        @DisplayName("连接被拒 → SERVICE_ERROR（服务不可达，不归因文档）")
        void connectionRefused(@TempDir Path tempDir) throws IOException {
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(closedPortUrl(), 1).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.SERVICE_ERROR);
            assertThat(outcome.detail()).contains("连接解析服务失败");
        }

        @Test
        @DisplayName("5xx → SERVICE_ERROR，可重试")
        void serverError(@TempDir Path tempDir) throws IOException {
            String url = startStub(500, "{\"detail\":\"boom\"}", 0);
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.SERVICE_ERROR);
            assertThat(outcome.retrySuggested()).isTrue();
        }

        @Test
        @DisplayName("413 → SERVICE_ERROR 且不建议重试（超过解析服务上限）")
        void payloadTooLarge(@TempDir Path tempDir) throws IOException {
            String url = startStub(413, "{\"detail\":\"too large\"}", 0);
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.SERVICE_ERROR);
            assertThat(outcome.retrySuggested()).isFalse();
            assertThat(outcome.detail()).contains("413");
        }

        @Test
        @DisplayName("422/400 → UNPARSEABLE（文档级错误，唯一允许引导重传）")
        void unparseable(@TempDir Path tempDir) throws IOException {
            String url = startStub(422, "{\"error\":\"bad doc\"}", 0);
            Path file = writeFile(tempDir, "bid.pdf", "pdf-bytes");

            SidecarParseOutcome outcome = adapterFor(url, 5).convertDocument(file, "bid.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.UNPARSEABLE);
        }
    }

    @Nested
    @DisplayName("降级与预算分档")
    class FallbackAndBudgetTests {

        @Test
        @DisplayName("服务不可达 + 纯文本小文件 → 本地降级成功（OK）")
        void localPlainTextFallback(@TempDir Path tempDir) throws IOException {
            Path file = writeFile(tempDir, "notes.txt", "纯文本招标条款内容");

            SidecarParseOutcome outcome = adapterFor(closedPortUrl(), 1).convertDocument(file, "notes.txt");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.OK);
            assertThat(outcome.content().fullText()).isEqualTo("纯文本招标条款内容");
        }

        @Test
        @DisplayName("服务不可达 + 二进制 docx → 保持 SERVICE_ERROR，不做本地 readString")
        void binaryNoFallback(@TempDir Path tempDir) throws IOException {
            Path file = writeFile(tempDir, "huge_bid.docx", "PK\u0003\u0004\u00FF\u00FE");

            SidecarParseOutcome outcome = adapterFor(closedPortUrl(), 1).convertDocument(file, "huge_bid.docx");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.SERVICE_ERROR);
            assertThat(outcome.content().fullText()).isEmpty();
        }

        @Test
        @DisplayName("预算按原始大小分档：>50MB 走大文件档，慢服务端不再触发读超时")
        void largeFileBudget_tierApplied(@TempDir Path tempDir) throws IOException {
            // 服务端 2s 后返回；若错误地按小文件档（base=1s）处理则必然超时
            String url = startStub(200, "{\"markdown\": \"大文件正文\"}", 2000);
            Path file = tempDir.resolve("huge.pdf");
            try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "rw")) {
                raf.setLength(60L * 1024 * 1024);
            }

            SidecarParseOutcome outcome = adapterFor(url, 1).convertDocument(file, "huge.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.OK);
            assertThat(outcome.content().fullText()).isEqualTo("大文件正文");
        }

        @Test
        @DisplayName("文件不存在 → SERVICE_ERROR（防御路径）")
        void missingFile(@TempDir Path tempDir) {
            SidecarParseOutcome outcome = adapterFor("http://127.0.0.1:1", 1)
                    .convertDocument(tempDir.resolve("missing.pdf"), "missing.pdf");

            assertThat(outcome.kind()).isEqualTo(SidecarParseOutcome.Kind.SERVICE_ERROR);
            assertThat(outcome.detail()).contains("临时文件不存在");
        }
    }
}
