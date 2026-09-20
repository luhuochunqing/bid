// Input: 临时文件路径 + 文件名（Sidecar 响应：markdown + 可选结构化 pages + warnings）
// Output: SidecarParseOutcome 分类结果（OK/TIMEOUT/SERVICE_ERROR/EMPTY_TEXT/UNPARSEABLE，不抛异常）
// Pos: compliance/service/ - 标书文档转换 Sidecar 流式适配器（HTTP Shell）
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.common.util.StringUtils;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.SidecarParseOutcome;
import com.xiyu.bid.compliance.domain.SidecarTimeoutBudget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 标书文档转换 Sidecar 流式适配器.
 * 对接 Sidecar (FastAPI /convert)，提取结构化分页、Markdown 与 SHA-256 哈希.
 *
 * <p>契约：永不抛异常，一切失败分类为 {@link SidecarParseOutcome}（spec 066）——
 * 超时与服务错误不再吞成空文档，调用方可据此输出准确文案.
 */
@Component
@Slf4j
public class SidecarStreamDocumentAdapter {

    private static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";
    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String sidecarUrl;
    private final String sharedKey;
    private final long baseTimeoutSeconds;

    public SidecarStreamDocumentAdapter(
            ObjectMapper objectMapper,
            @Value("${app.score-parse.sidecar-url:${app.doc-insight.sidecar-url:http://localhost:8000}}") String sidecarUrl,
            @Value("${app.score-parse.sidecar-shared-key:${SIDECAR_SHARED_KEY:}}") String sharedKey,
            @Value("${app.compliance.sidecar.timeout-seconds:60}") long baseTimeoutSeconds) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.sidecarUrl = sidecarUrl.endsWith("/")
                ? sidecarUrl.substring(0, sidecarUrl.length() - 1)
                : sidecarUrl;
        this.sharedKey = sharedKey == null ? "" : sharedKey.trim();
        this.baseTimeoutSeconds = baseTimeoutSeconds;
    }

    /**
     * 将临时文件送入 Sidecar 解析，输出分类结果.
     *
     * <p>等待上限按<b>原始文件大小</b>分档（{@link SidecarTimeoutBudget}），
     * 媒体瘦身不降档；连接建立超时归服务错误，读超时归解析超时.
     */
    public SidecarParseOutcome convertDocument(final Path filePath, final String fileName) {
        if (filePath == null || !Files.exists(filePath)) {
            return SidecarParseOutcome.serviceError("临时文件不存在");
        }

        String rawContentHash = SidecarPayloadParser.computeSha256(filePath);
        long originalSizeBytes = fileSizeOf(filePath);
        long limitSeconds = limitSecondsFor(filePath);
        long startedAtNanos = System.nanoTime();
        Path uploadFilePath = filePath;
        boolean needsCleanup = false;

        SidecarParseOutcome outcome;
        try {
            if (DocxMediaFilter.shouldFilter(filePath, fileName, DocxMediaFilter.DEFAULT_THRESHOLD_BYTES)) {
                Path slimPath = DocxMediaFilter.filterMediaToTempFile(filePath);
                if (slimPath != null && !slimPath.equals(filePath)) {
                    uploadFilePath = slimPath;
                    needsCleanup = true;
                }
            }
            HttpResponse<String> response = send(uploadFilePath, fileName, limitSeconds);
            outcome = classify(response, fileName, rawContentHash, originalSizeBytes, limitSeconds, startedAtNanos);
        } catch (HttpConnectTimeoutException e) {
            outcome = loggedFailure(SidecarParseOutcome.serviceError("连接解析服务超时（10s）"),
                    fileName, originalSizeBytes, limitSeconds);
        } catch (HttpTimeoutException e) {
            outcome = loggedFailure(SidecarParseOutcome.timeout(
                            "文件 " + sizeMbOf(originalSizeBytes) + "MB 未在 " + limitSeconds + "s 内完成"),
                    fileName, originalSizeBytes, limitSeconds);
        } catch (IOException e) {
            outcome = loggedFailure(SidecarParseOutcome.serviceError("连接解析服务失败：" + e.getMessage()),
                    fileName, originalSizeBytes, limitSeconds);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            outcome = loggedFailure(SidecarParseOutcome.serviceError("解析调用被中断"),
                    fileName, originalSizeBytes, limitSeconds);
        } catch (RuntimeException e) {
            outcome = loggedFailure(SidecarParseOutcome.serviceError("解析调用异常：" + e.getMessage()),
                    fileName, originalSizeBytes, limitSeconds);
        } finally {
            if (needsCleanup && uploadFilePath != null) {
                deleteQuietly(uploadFilePath);
            }
        }

        if (outcome.isOk()) {
            return outcome;
        }
        return withLocalPlainTextFallback(filePath, fileName, rawContentHash, outcome);
    }

    private HttpResponse<String> send(final Path uploadFilePath, final String fileName, final long limitSeconds)
            throws IOException, InterruptedException {
        String boundary = "xiyu-compliance-" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(sidecarUrl + "/convert"))
                .timeout(Duration.ofSeconds(limitSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!sharedKey.isBlank()) {
            requestBuilder.header(SIDECAR_KEY_HEADER, sharedKey);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(multipartPrefix(boundary, fileName)),
                        HttpRequest.BodyPublishers.ofFile(uploadFilePath),
                        HttpRequest.BodyPublishers.ofByteArray(multipartSuffix(boundary))))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    /**
     * 状态码与负载分类（含可观测日志）.
     */
    private SidecarParseOutcome classify(final HttpResponse<String> response, final String fileName,
                                         final String rawContentHash, final long originalSizeBytes,
                                         final long limitSeconds, final long startedAtNanos) {
        int status = response.statusCode();
        long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
        long sizeMb = sizeMbOf(originalSizeBytes);
        if (status >= 200 && status < 300) {
            JsonNode root;
            try {
                root = objectMapper.readTree(response.body());
            } catch (JsonProcessingException e) {
                log.warn("Sidecar 响应 JSON 非法（file={}, status=200, duration={}ms）: {}",
                        fileName, durationMs, e.getMessage());
                return SidecarParseOutcome.serviceError("解析服务响应格式异常");
            }
            if (root == null || root.isMissingNode()) {
                log.warn("Sidecar 响应为空（file={}, status=200, duration={}ms）", fileName, durationMs);
                return SidecarParseOutcome.serviceError("解析服务响应为空");
            }
            List<String> warnings = SidecarPayloadParser.warningsOf(root);
            DocumentContent content = SidecarPayloadParser.documentFromSidecarPayload(fileName, rawContentHash, root);
            if (content.fullText().isBlank() && content.pages().isEmpty()) {
                log.warn("Sidecar 解析结果无文本（file={}, sizeMB={}, limit={}s, duration={}ms, warnings={}）",
                        fileName, sizeMb, limitSeconds, durationMs, warnings);
                return SidecarParseOutcome.emptyText("warnings=" + warnings, warnings);
            }
            log.info("Sidecar 解析成功（file={}, sizeMB={}, limit={}s, duration={}ms, pages={}, chars={}, warnings={}）",
                    fileName, sizeMb, limitSeconds, durationMs,
                    content.pages().size(), content.fullText().length(), warnings);
            return SidecarParseOutcome.ok(content, warnings);
        }

        log.warn("Sidecar /convert 返回状态码 {}（file={}, sizeMB={}, limit={}s, duration={}ms）: {}",
                status, fileName, sizeMb, limitSeconds, durationMs, StringUtils.truncate(response.body(), 300));
        if (status == 413) {
            return SidecarParseOutcome.oversize("文件超过解析服务大小上限（413）");
        }
        if (status == 400 || status == 415 || status == 422) {
            return SidecarParseOutcome.unparseable("解析服务返回 " + status);
        }
        return SidecarParseOutcome.serviceError("解析服务返回 " + status);
    }

    /**
     * 传输/服务失败时的既有降级：纯文本小文件（≤5MB）本地读取，
     * 内容非空则视为 OK（内容完整）；其余保持原失败分类.
     */
    private SidecarParseOutcome withLocalPlainTextFallback(final Path filePath, final String fileName,
                                                           final String rawContentHash,
                                                           final SidecarParseOutcome failure) {
        if (!SidecarPayloadParser.isSmallPlainTextFile(fileName, filePath)) {
            return failure;
        }
        String localText = SidecarPayloadParser.readLocalPlainText(filePath);
        if (localText.isBlank()) {
            return failure;
        }
        log.info("Sidecar 不可用（{}），纯文本文件本地降级成功（file={}, chars={}）",
                failure.kind(), fileName, localText.length());
        return SidecarParseOutcome.ok(
                SidecarPayloadParser.buildDocumentContent(fileName, rawContentHash, localText),
                failure.warnings());
    }

    /**
     * 生效等待上限（按<b>原始文件</b>大小；媒体瘦身不降档，spec 066 FR-010）.
     * 包级可见以便单测用稀疏文件锁定档位来源.
     */
    long limitSecondsFor(final Path originalFile) {
        return SidecarTimeoutBudget.secondsFor(baseTimeoutSeconds, fileSizeOf(originalFile));
    }

    private static long sizeMbOf(final long sizeBytes) {
        return sizeBytes <= 0 ? -1 : sizeBytes / BYTES_PER_MB;
    }

    /** 失败路径统一 WARN 日志（R11：超时/连接失败等生产主失败形态必须可观测）. */
    private SidecarParseOutcome loggedFailure(final SidecarParseOutcome outcome, final String fileName,
                                              final long originalSizeBytes, final long limitSeconds) {
        log.warn("Sidecar 解析未成功（file={}, sizeMB={}, limit={}s, kind={}, detail={}）",
                fileName, sizeMbOf(originalSizeBytes), limitSeconds, outcome.kind(), outcome.detail());
        return outcome;
    }

    private static long fileSizeOf(final Path path) {
        try {
            return Files.size(path);
        } catch (IOException | RuntimeException e) {
            return -1L;
        }
    }

    private static void deleteQuietly(final Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn("清理 Docx 瘦身临时文件失败 {}: {}", path, e.getMessage());
        }
    }

    private static byte[] multipartPrefix(final String boundary, final String fileName) {
        String safeName = fileName != null ? fileName.replace("\"", "") : "file.bin";
        String s = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + safeName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] multipartSuffix(final String boundary) {
        String s = "\r\n--" + boundary + "--\r\n";
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
