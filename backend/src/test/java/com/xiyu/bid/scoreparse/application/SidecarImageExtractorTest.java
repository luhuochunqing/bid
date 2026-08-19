// Input: 内嵌 HttpServer 模拟 /convert-images（200 JSON/超限/422/multipart 到达/鉴权头）
// Output: 抽图返回图片清单 / 响应超限异常 / 422 语义 / 防御式过滤 / 上传与鉴权断言
// Pos: scoreparse/application — SidecarImageExtractor 单元测试（spec 048 T006，契约 contracts/sidecar-images-api.md）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SidecarImageExtractorTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private SidecarImageExtractor extractor;
    private Path docFile;
    private final AtomicReference<String> receivedContentType = new AtomicReference<>();
    private final AtomicReference<String> receivedSidecarKey = new AtomicReference<>();
    private final AtomicReference<byte[]> receivedBody = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        docFile = tempDir.resolve("bid-doc.docx");
        Files.write(docFile, "DOCX-BYTES-0123456789".getBytes(StandardCharsets.UTF_8));
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serveConvertImages(int status, String body) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/convert-images", exchange -> {
            receivedContentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedSidecarKey.set(exchange.getRequestHeaders().getFirst("X-Sidecar-Key"));
            receivedBody.set(exchange.getRequestBody().readAllBytes());
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, bodyBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bodyBytes);
            }
            exchange.close();
        });
    }

    private SidecarImageExtractor newExtractor(long maxResponseBytes, String sharedKey) {
        return new SidecarImageExtractor(
                "http://127.0.0.1:" + server.getAddress().getPort(),
                sharedKey,
                maxResponseBytes,
                30,
                new ObjectMapper());
    }

    private static String imageJson(int index, String mimeType, String section, Integer pageHint,
                                    int width, int height, String base64) {
        return """
                {"index":%d,"mimeType":"%s","section":%s,"pageHint":%s,"width":%d,"height":%d,"base64":"%s"}"""
                .formatted(index, mimeType,
                        section == null ? "null" : "\"" + section + "\"",
                        pageHint == null ? "null" : pageHint,
                        width, height, base64);
    }

    @Test
    void extractFile_returnsImagesAndMultipartUpload() throws Exception {
        extractor = newExtractor(26214400, "secret-key");
        serveConvertImages(200, """
                {"images":[%s,%s],"truncated":true,"warnings":["图片预算截断"]}"""
                .formatted(
                        imageJson(0, "image/png", "3.2 业绩要求", null, 800, 600, "aGk="),
                        imageJson(1, "image/jpeg", null, 12, 1024, 768, "eHk=")));

        SidecarImageExtraction extraction = extractor.extract(docFile, "投标文件.docx");

        assertThat(extraction.truncated()).isTrue();
        assertThat(extraction.images()).hasSize(2);
        assertThat(extraction.images().get(0).section()).isEqualTo("3.2 业绩要求");
        assertThat(extraction.images().get(0).pageHint()).isNull();
        assertThat(extraction.images().get(1).pageHint()).isEqualTo(12);
        assertThat(extraction.images().get(1).base64()).isEqualTo("eHk=");
        // 上传侧：multipart + 文件字节 + 鉴权头（契约：X-Sidecar-Key 复用现有认证）
        assertThat(receivedContentType.get()).startsWith("multipart/form-data");
        assertThat(new String(receivedBody.get(), StandardCharsets.ISO_8859_1))
                .contains("DOCX-BYTES-0123456789")
                .contains("name=\"file\"");
        assertThat(receivedSidecarKey.get()).isEqualTo("secret-key");
    }

    @Test
    void extractByteArray_sendsContentDirectly() throws Exception {
        extractor = newExtractor(26214400, "");
        serveConvertImages(200, "{\"images\":[" + imageJson(0, "image/png", null, null, 10, 10, "aGk=")
                + "],\"truncated\":false,\"warnings\":[]}");

        SidecarImageExtraction extraction = extractor.extract(
                "DOCX-BYTES".getBytes(StandardCharsets.UTF_8), "投标文件.docx");

        assertThat(extraction.images()).hasSize(1);
        assertThat(extraction.truncated()).isFalse();
        assertThat(new String(receivedBody.get(), StandardCharsets.ISO_8859_1))
                .contains("DOCX-BYTES")
                .contains("name=\"file\"");
    }

    @Test
    void extract_responseOverByteLimit_throwsOversized() {
        // maxResponseBytes=10，响应体 60+ 字节 → 读满即中止抛异常
        extractor = newExtractor(10, "");
        serveConvertImages(200, "{\"images\":[" + imageJson(0, "image/png", null, null, 800, 600, "aGk=")
                + "],\"truncated\":false,\"warnings\":[]}");

        assertThatThrownBy(() -> extractor.extract(docFile, "投标文件.docx"))
                .isInstanceOf(OversizedImageResponseException.class)
                .hasMessageContaining("纯文本");
    }

    @Test
    void extract_invalidMimeOrBlankBase64_skippedDefensively() throws Exception {
        extractor = newExtractor(26214400, "");
        serveConvertImages(200, "{\"images\":[%s,%s,%s],\"truncated\":false,\"warnings\":[]}"
                .formatted(
                        imageJson(0, "image/tiff", null, null, 800, 600, "aGk="),
                        imageJson(1, "image/png", null, null, 800, 600, "  "),
                        imageJson(2, "image/gif", "附图", null, 100, 100, "Z2lm")));

        SidecarImageExtraction extraction = extractor.extract(docFile, "投标文件.docx");

        // 非法 mimeType 与空 base64 逐项跳过（Principle VII），合法项保留
        assertThat(extraction.images()).hasSize(1);
        assertThat(extraction.images().get(0).mimeType()).isEqualTo("image/gif");
    }

    @Test
    void extract_sidecarUnprocessable_throwsIllegalState() {
        extractor = newExtractor(26214400, "");
        serveConvertImages(422, "{\"error\":\"文档无法解析\"}");

        assertThatThrownBy(() -> extractor.extract(docFile, "投标文件.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("无法解析");
    }

    @Test
    void extract_sidecarError_throwsIllegalState() {
        extractor = newExtractor(26214400, "");
        serveConvertImages(500, "boom");

        assertThatThrownBy(() -> extractor.extract(docFile, "投标文件.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }
}
