// Input: 内嵌 HttpServer 模拟 /convert-stream（200 文本/超限/422/multipart 到达）
// Output: 流式提取返回文本 / char 超限异常 / 422 语义 / 上传 multipart 断言
// Pos: scoreparse/application — SidecarStreamTextExtractor 单元测试（spec 045 T009）
package com.xiyu.bid.scoreparse.application;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarStreamTextExtractorTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private SidecarStreamTextExtractor extractor;
    private Path docFile;
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();
    private final AtomicInteger requestCount = new AtomicInteger();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        docFile = tempDir.resolve("bid-doc.pdf");
        Files.write(docFile, "PDF-BYTES-0123456789".getBytes(StandardCharsets.UTF_8));
        extractor = new SidecarStreamTextExtractor(
                "http://127.0.0.1:" + server.getAddress().getPort(), "", 10, 30);
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serveConvertStream(int status, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/convert-stream", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(status, bodyBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bodyBytes);
            }
            exchange.close();
        });
    }

    @Test
    void extract_returnsStreamedTextAndMultipartUpload() throws Exception {
        serveConvertStream(200, "投标文件正文");

        String text = extractor.extract(docFile, "投标文件.pdf");

        assertThat(text).isEqualTo("投标文件正文");
        // 上传侧是 multipart，且 body 含文件字节（流式上传契约）
        assertThat(receivedContentType.get()).startsWith("multipart/form-data");
        assertThat(new String(receivedBody.get(), StandardCharsets.ISO_8859_1))
                .contains("PDF-BYTES-0123456789")
                .contains("name=\"file\"");
    }

    @Test
    void extract_overTextCharLimit_throwsOversizedBidText() {
        // maxTextChars=10，返回 20 字符 → 读满即中止抛异常
        serveConvertStream(200, "01234567890123456789");

        assertThatThrownBy(() -> extractor.extract(docFile, "投标文件.pdf"))
                .isInstanceOf(OversizedBidTextException.class)
                .hasMessageContaining("500 万字");
    }

    @Test
    void extract_sidecarUnprocessable_throwsIllegalState() {
        serveConvertStream(422, "{\"error\":\"文档无法解析\"}");

        assertThatThrownBy(() -> extractor.extract(docFile, "投标文件.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("投标文件无法解析");
    }

    @Test
    void extract_sidecarError_throwsIllegalState() {
        serveConvertStream(500, "boom");

        assertThatThrownBy(() -> extractor.extract(docFile, "投标文件.pdf"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }

    @Test
    void extract_retriesTransientSidecarFailure() throws Exception {
        byte[] successBody = "重试后正文".getBytes(StandardCharsets.UTF_8);
        server.createContext("/convert-stream", exchange -> {
            int request = requestCount.incrementAndGet();
            exchange.getRequestBody().readAllBytes();
            if (request < 3) {
                exchange.sendResponseHeaders(503, 0);
                exchange.close();
                return;
            }
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
            exchange.sendResponseHeaders(200, successBody.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(successBody);
            }
        });

        assertThat(extractor.extract(docFile, "投标文件.pdf")).isEqualTo("重试后正文");
        assertThat(requestCount).hasValue(3);
    }
}
