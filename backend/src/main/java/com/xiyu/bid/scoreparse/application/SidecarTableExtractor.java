// Input: OnDisk Path 或 InMemory byte[] + 文件名
// Output: SidecarTableExtraction（结构化表格数据，含合并单元格信息）
// Pos: scoreparse/application — /extract-tables 客户端（Phase 1 结构化表格提取通道）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
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
import java.util.UUID;

/**
 * sidecar /extract-tables 客户端。
 * <p>双入口：OnDisk 临时文件流式上传（ofFile）；InMemory 全量 byte[] 直传（ofByteArray）。
 * 响应整包读，异常降级返回 empty（调用方负责回退到 markdown 路径）。
 */
@Component
@Slf4j
public class SidecarTableExtractor {

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
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
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
        try (InputStream body = response.body()) {
            return parseResponse(body.readAllBytes());
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
