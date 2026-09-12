// Input: 内嵌 HttpServer 模拟 /extract-tables（v1 正常/v2 回落/头不匹配/500）
// Output: v1 解析成功 / 版本不匹配 fail-fast（含双方版本号）/ HTTP 错误透出
// Pos: scoreparse/application — SidecarTableExtractor 单元测试（发布闭环 Stage D）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
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

class SidecarTableExtractorTest {

    private static final String V1_BODY = """
            {"tables":[{"tableIndex":0,"rows":[[\
            {"text":"评分因素","rowspan":1,"colspan":1,"isMergedContinue":false},\
            {"text":"分值","rowspan":1,"colspan":1,"isMergedContinue":false}]],\
            "isScoreTable":true}],"warnings":[]}""";

    private static final String V2_BODY = """
            {"schemaVersion":"2.0",\
            "artifact":{"documentId":"sha256:abc","sha256":"abc",\
            "fileType":"DOCX","extractionVersion":"table-v2"},\
            "tables":[],"warnings":[]}""";

    @TempDir
    Path tempDir;

    private HttpServer server;
    private SidecarTableExtractor extractor;
    private final AtomicReference<String> receivedSchemaVersion = new AtomicReference<>();

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        extractor = new SidecarTableExtractor(
                "http://127.0.0.1:" + server.getAddress().getPort(), "", 10, new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    private void serveExtractTables(int status, String body, String responseSchemaVersion) {
        byte[] bodyBytes = body.getBytes(StandardCharsets.UTF_8);
        server.createContext("/extract-tables", exchange -> {
            receivedSchemaVersion.set(exchange.getRequestHeaders().getFirst("X-Schema-Version"));
            exchange.getRequestBody().readAllBytes();
            if (responseSchemaVersion != null) {
                exchange.getResponseHeaders().set("X-Schema-Version", responseSchemaVersion);
            }
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bodyBytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bodyBytes);
            }
            exchange.close();
        });
    }

    @Test
    void extract_sendsV1HeaderAndParsesV1Body() throws Exception {
        serveExtractTables(200, V1_BODY, "1.0");

        SidecarTableExtraction extraction = extractor.extract("bytes".getBytes(StandardCharsets.UTF_8), "bid.docx");

        assertThat(receivedSchemaVersion.get()).isEqualTo("1.0");
        assertThat(extraction.tables()).hasSize(1);
        assertThat(extraction.tables().get(0).rows().get(0).get(0).text()).isEqualTo("评分因素");
        assertThat(extraction.tables().get(0).isScoreTable()).isTrue();
    }

    @Test
    void extract_onDiskPath_sendsV1Header() throws Exception {
        serveExtractTables(200, V1_BODY, "1.0");
        Path docFile = tempDir.resolve("bid.docx");
        Files.write(docFile, "DOCX-BYTES".getBytes(StandardCharsets.UTF_8));

        SidecarTableExtraction extraction = extractor.extract(docFile, "bid.docx");

        assertThat(receivedSchemaVersion.get()).isEqualTo("1.0");
        assertThat(extraction.tables()).hasSize(1);
    }

    @Test
    void extract_v2BodyWithoutHeader_throwsVersionMismatch() {
        serveExtractTables(200, V2_BODY, null);

        assertThatThrownBy(() -> extractor.extract("bytes".getBytes(StandardCharsets.UTF_8), "bid.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1.0")
                .hasMessageContaining("2.0");
    }

    @Test
    void extract_v2BodyWithV2Header_throwsVersionMismatch() {
        serveExtractTables(200, V2_BODY, "2.0");

        assertThatThrownBy(() -> extractor.extract("bytes".getBytes(StandardCharsets.UTF_8), "bid.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("X-Schema-Version=1.0")
                .hasMessageContaining("2.0");
    }

    @Test
    void extract_v1BodyWithMismatchedHeader_throwsVersionMismatch() {
        serveExtractTables(200, V1_BODY, "2.0");

        assertThatThrownBy(() -> extractor.extract("bytes".getBytes(StandardCharsets.UTF_8), "bid.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("响应头")
                .hasMessageContaining("2.0");
    }

    @Test
    void extract_v2ArtifactMarkerWithoutSchemaVersion_throwsVersionMismatch() {
        serveExtractTables(200, "{\"artifact\":{},\"tables\":[],\"warnings\":[]}", null);

        assertThatThrownBy(() -> extractor.extract("bytes".getBytes(StandardCharsets.UTF_8), "bid.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1.0")
                .hasMessageContaining("v2 DocumentArtifact");
    }

    @Test
    void extract_sidecarError_throwsIllegalState() {
        serveExtractTables(500, "boom", null);

        assertThatThrownBy(() -> extractor.extract("bytes".getBytes(StandardCharsets.UTF_8), "bid.docx"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HTTP 500");
    }
}
