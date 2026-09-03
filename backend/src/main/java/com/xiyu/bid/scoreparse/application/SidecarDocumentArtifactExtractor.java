// Input: Sidecar DocumentArtifact schemaVersion=2.0 JSON
// Output: DocumentArtifact 值对象（含 artifact/tables/warnings）
// Pos: scoreparse/application — /extract-tables 客户端的 v2 解析路径（Phase 1）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.StructureValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/**
 * Sidecar DocumentArtifact v2 客户端（spec 059 Phase 1 FR-01）。
 * <p>与 {@link SidecarTableExtractor} 相比：要求 Sidecar 返回 schemaVersion=2.0 的
 * {@link DocumentArtifact} 形态（含 cellId / XML 路径 / 合并区域），让后端能进入
 * CanonicalTableGrid → ScoreFact 的确定性链路。
 * <p>两条规则：
 * <ul>
 *   <li>Sidecar 拒绝旧 schema 时（旧 header X-Schema-Version: 1.x 仍可用），客户端不会
 *       自动降级；调用方必须显式选择走 {@link SidecarTableExtractor}。</li>
 *   <li>Sidecar HTTP 错误或解析失败时，本类向上抛出 {@link DocumentArtifactUnavailable}，
 *       调用方按 {@link StructureValidationResult.Status#STRUCTURE_INCOMPLETE} 处理。</li>
 * </ul>
 * <p>影子链路现状（审核修复）：本 Bean 已进容器，生产切换在 Phase 3/4 进行；
 * 当前消费方为 {@link ScoreFactShadowPipeline}（只读影子执行）。
 */
@Slf4j
@Component
public class SidecarDocumentArtifactExtractor {

    private final HttpClient httpClient;
    private final String sidecarUrl;
    private final String sharedKey;
    private final long extractTimeoutSeconds;
    private final ObjectMapper objectMapper;

    public SidecarDocumentArtifactExtractor(
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

    /**
     * 主入口：上传 docx 字节并解析为 DocumentArtifact。
     *
     * @throws DocumentArtifactUnavailable sidecar 不可达 / 4xx-5xx / 解析失败时抛出，
     *         调用方应将 {@link StructureValidationResult.Status#STRUCTURE_INCOMPLETE}
     *         写入任务并保留现有 {@code score_item}。
     */
    public DocumentArtifact extract(byte[] content, String fileName)
            throws IOException, InterruptedException {
        String boundary = "xiyu-doc-artifact-" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(sidecarUrl + "/extract-tables"))
                .timeout(Duration.ofSeconds(extractTimeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                // Force v2: schemaVersion=2.0 DocumentArtifact.
                .header("X-Schema-Version", DocumentArtifact.SUPPORTED_SCHEMA_VERSION);
        if (!sharedKey.isBlank()) {
            requestBuilder.header(SidecarMultipart.SIDECAR_KEY_HEADER, sharedKey);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(SidecarMultipart.prefix(boundary, fileName)),
                        HttpRequest.BodyPublishers.ofByteArray(content),
                        HttpRequest.BodyPublishers.ofByteArray(SidecarMultipart.suffix(boundary))))
                .build();
        HttpResponse<InputStream> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (IOException | InterruptedException e) {
            log.warn("sidecar /extract-tables request failed: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
                throw (InterruptedException) e;
            }
            throw new DocumentArtifactUnavailable(
                    "sidecar /extract-tables unreachable: " + e.getMessage(), e);
        }
        int status = response.statusCode();
        if (status == 422) {
            // ValueError (corrupted docx) → not STRUCTURE_INCOMPLETE per se, but
            // callers should treat it the same way (no fallback to LLM).
            throw new DocumentArtifactUnavailable(
                    "sidecar returned 422: " + SidecarMultipart.readErrorSnippet(response.body()));
        }
        if (status < 200 || status >= 300) {
            throw new DocumentArtifactUnavailable(
                    "sidecar HTTP " + status + ": " + SidecarMultipart.readErrorSnippet(response.body()));
        }
        byte[] bodyBytes;
        try (InputStream body = response.body()) {
            bodyBytes = body.readAllBytes();
        }
        DocumentArtifact artifact;
        try {
            artifact = objectMapper.readValue(bodyBytes, DocumentArtifact.class);
        } catch (IOException e) {
            throw new DocumentArtifactUnavailable(
                    "sidecar response is not a valid DocumentArtifact: " + e.getMessage(), e);
        }
        if (artifact == null || artifact.artifact() == null) {
            throw new DocumentArtifactUnavailable("sidecar response missing artifact header");
        }
        // Defensive: even when Sidecar matches schemaVersion, reject payloads with
        // a mismatched value (downstream validation gates on this).
        return artifact;
    }

    /**
     * Raised when the DocumentArtifact v2 contract cannot be obtained from sidecar.
     * <p>Calling code must translate this to {@code STRUCTURE_INCOMPLETE} and
     * refuse to invoke the LLM / WeightNormalizationPolicy (Phase 1 硬不变量).
     */
    public static class DocumentArtifactUnavailable extends RuntimeException {
        public DocumentArtifactUnavailable(String message) {
            super(message);
        }

        public DocumentArtifactUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
