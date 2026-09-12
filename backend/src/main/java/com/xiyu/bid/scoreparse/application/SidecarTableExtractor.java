// Input: OnDisk Path 或 InMemory byte[] + 文件名
// Output: SidecarTableExtraction（结构化表格数据，含合并单元格信息）
// Pos: scoreparse/application — /extract-tables 客户端（Phase 1 结构化表格提取通道）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * sidecar /extract-tables 客户端。
 * <p>双入口：OnDisk 临时文件流式上传（ofFile）；InMemory 全量 byte[] 直传（ofByteArray）。
 * 响应整包读，异常降级返回 empty（调用方负责回退到 markdown 路径）。
 */
@Component
@Slf4j
public class SidecarTableExtractor {

    /** 本客户端期望的 Sidecar 表格协议版本（legacy v1 形态）。 */
    public static final String EXPECTED_SCHEMA_VERSION = "1.0";

    /** Sidecar 版本协商头（请求发送 + 响应回显）。 */
    public static final String SCHEMA_VERSION_HEADER = "X-Schema-Version";

    private final HttpClient httpClient;
    private final String sidecarUrl;
    private final String sharedKey;
    private final long extractTimeoutSeconds;
    private final ObjectMapper objectMapper;

    public SidecarTableExtractor(
            @Value("${app.score-parse.sidecar-url:http://localhost:8000}") String sidecarUrl,
            @Value("${app.score-parse.sidecar-shared-key:${SIDECAR_SHARED_KEY:}}") String sharedKey,
            @Value("${app.score-parse.bid-file.extract-timeout-seconds:600}") long extractTimeoutSeconds,
            ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.sidecarUrl = sidecarUrl.endsWith("/")
                ? sidecarUrl.substring(0, sidecarUrl.length() - 1)
                : sidecarUrl;
        this.sharedKey = sharedKey == null ? "" : sharedKey.trim();
        this.extractTimeoutSeconds = extractTimeoutSeconds;
        this.objectMapper = objectMapper;
    }

    /** OnDisk 路径：磁盘临时文件流式上传。 */
    public SidecarTableExtraction extract(Path file, String fileName)
            throws IOException, InterruptedException {
        return post(HttpRequest.BodyPublishers.ofFile(file), fileName);
    }

    /** InMemory 路径：全量 byte[] 直传。 */
    public SidecarTableExtraction extract(byte[] content, String fileName)
            throws IOException, InterruptedException {
        return post(HttpRequest.BodyPublishers.ofByteArray(content), fileName);
    }

    private SidecarTableExtraction post(HttpRequest.BodyPublisher fileBody, String fileName)
            throws IOException, InterruptedException {
        String boundary = "xiyu-extract-tables-" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(sidecarUrl + "/extract-tables"))
                .timeout(Duration.ofSeconds(extractTimeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header(SCHEMA_VERSION_HEADER, EXPECTED_SCHEMA_VERSION);
        if (!sharedKey.isBlank()) {
            requestBuilder.header(SidecarMultipart.SIDECAR_KEY_HEADER, sharedKey);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(SidecarMultipart.prefix(boundary, fileName)),
                        fileBody,
                        HttpRequest.BodyPublishers.ofByteArray(SidecarMultipart.suffix(boundary))))
                .build();
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "调用结构化表格提取服务失败 HTTP " + status + "：" + SidecarMultipart.readErrorSnippet(response.body()));
        }
        String responseSchemaVersion =
                response.headers().firstValue(SCHEMA_VERSION_HEADER).orElse(null);
        byte[] bodyBytes;
        try (InputStream body = response.body()) {
            bodyBytes = body.readAllBytes();
        }
        requireV1Contract(readTreeLenient(bodyBytes), responseSchemaVersion);
        return parseResponse(bodyBytes);
    }

    /**
     * v1 协议守卫：无头回落到 v2 / 版本不匹配时直接抛明确异常（含双方版本号）。
     * <p>背景：Sidecar 默认返回 v2 DocumentArtifact；若请求头被代理剥离或 Sidecar 已升级，
     * 本客户端会拿到 v2 形态。若不拦截，Jackson 会以晦涩的反序列化错误失败，或更糟——
     * 静默解析出空表格回退到 lossy Markdown。fail-fast 让发布冒烟与日志直接定位到版本。
     *
     * @param body 响应体 JSON 树（非 JSON 时为 null，交由 parseResponse 抛解析错误）
     * @param responseSchemaVersion 响应头 X-Schema-Version（Sidecar 可能不回显，此时为 null）
     * @throws IllegalStateException 响应头或响应体表明非 v1 时抛出，消息含期望/实际版本号
     */
    void requireV1Contract(JsonNode body, String responseSchemaVersion) {
        Optional<String> reason = versionMismatchReason(body, responseSchemaVersion);
        if (reason.isPresent()) {
            throw new IllegalStateException(reason.get());
        }
    }

    /**
     * 纯决策函数：判断响应是否为本客户端可消费的 v1 形态。
     * <p>无 IO、无日志、无状态，返回 Optional 描述违规原因（空 = 通过）。
     */
    static Optional<String> versionMismatchReason(JsonNode body, String responseSchemaVersion) {
        if (responseSchemaVersion != null
                && !responseSchemaVersion.isBlank()
                && !responseSchemaVersion.startsWith("1.")) {
            return Optional.of("Sidecar 表格提取协议版本不匹配：期望 "
                    + SCHEMA_VERSION_HEADER + "=" + EXPECTED_SCHEMA_VERSION
                    + "，实际响应头 " + SCHEMA_VERSION_HEADER + "=" + responseSchemaVersion
                    + "。请升级调用方到 SidecarDocumentArtifactExtractor 或回退 Sidecar 版本。");
        }
        if (body != null && body.isObject()) {
            JsonNode schemaVersion = body.get("schemaVersion");
            if (schemaVersion != null && schemaVersion.isTextual()
                    && !schemaVersion.asText().startsWith("1.")) {
                return Optional.of("Sidecar 表格提取协议版本不匹配：期望 "
                        + SCHEMA_VERSION_HEADER + "=" + EXPECTED_SCHEMA_VERSION
                        + "，实际响应体 schemaVersion=" + schemaVersion.asText()
                        + "（Sidecar 回落到默认 v2 DocumentArtifact，可能是请求头被代理剥离或 Sidecar 已升级）。"
                        + "请升级调用方到 SidecarDocumentArtifactExtractor 或回退 Sidecar 版本。");
            }
            if (body.has("artifact")) {
                return Optional.of("Sidecar 表格提取协议版本不匹配：期望 "
                        + SCHEMA_VERSION_HEADER + "=" + EXPECTED_SCHEMA_VERSION
                        + "，实际响应体为 v2 DocumentArtifact 形态（含 artifact 头且无 v1 schemaVersion）。"
                        + "请升级调用方到 SidecarDocumentArtifactExtractor 或回退 Sidecar 版本。");
            }
        }
        return Optional.empty();
    }

    private JsonNode readTreeLenient(byte[] bodyBytes) {
        try {
            return objectMapper.readTree(bodyBytes);
        } catch (IOException e) {
            return null;
        }
    }

    private SidecarTableExtraction parseResponse(byte[] bodyBytes) throws IOException {
        ResponsePayload payload = objectMapper.readValue(bodyBytes, ResponsePayload.class);
        if (payload == null || payload.tables == null) {
            return SidecarTableExtraction.empty();
        }
        List<SidecarTableExtraction.TablePayload> tables = new ArrayList<>();
        for (TablePayloadRaw item : payload.tables) {
            if (item == null) continue;
            List<List<SidecarTableExtraction.CellPayload>> rows = new ArrayList<>();
            if (item.rows != null) {
                for (List<CellPayloadRaw> rawRow : item.rows) {
                    if (rawRow == null) continue;
                    List<SidecarTableExtraction.CellPayload> row = new ArrayList<>();
                    for (CellPayloadRaw cell : rawRow) {
                        if (cell == null) continue;
                        row.add(new SidecarTableExtraction.CellPayload(
                                cell.text, cell.rowspan, cell.colspan, cell.isMergedContinue));
                    }
                    rows.add(row);
                }
            }
            tables.add(new SidecarTableExtraction.TablePayload(item.tableIndex, rows, item.isScoreTable));
        }
        return new SidecarTableExtraction(tables, payload.warnings);
    }

    private static final class ResponsePayload {
        public List<TablePayloadRaw> tables;
        public List<String> warnings;
    }

    private static final class TablePayloadRaw {
        public int tableIndex;
        public List<List<CellPayloadRaw>> rows;
        public boolean isScoreTable;
    }

    private static final class CellPayloadRaw {
        public String text;
        public int rowspan;
        public int colspan;
        public boolean isMergedContinue;
    }
}
