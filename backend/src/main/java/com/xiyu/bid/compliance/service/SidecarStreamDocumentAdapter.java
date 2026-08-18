package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.DocumentContent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 标书文档转换 Sidecar 流式适配器.
 * 对接 Sidecar (FastAPI /convert)，提取结构化分页、Markdown 与 SHA-256 哈希.
 */
@Component
@Slf4j
public class SidecarStreamDocumentAdapter {

    private static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String sidecarUrl;
    private final String sharedKey;
    private final long timeoutSeconds;

    public SidecarStreamDocumentAdapter(
            ObjectMapper objectMapper,
            @Value("${app.score-parse.sidecar-url:${app.doc-insight.sidecar-url:http://localhost:8000}}") String sidecarUrl,
            @Value("${app.score-parse.sidecar-shared-key:${SIDECAR_SHARED_KEY:}}") String sharedKey,
            @Value("${app.compliance.sidecar.timeout-seconds:60}") long timeoutSeconds) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
        this.sidecarUrl = sidecarUrl.endsWith("/")
                ? sidecarUrl.substring(0, sidecarUrl.length() - 1)
                : sidecarUrl;
        this.sharedKey = sharedKey == null ? "" : sharedKey.trim();
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * 将临时文件送入 Sidecar 解析，输出结构化 DocumentContent.
     */
    public DocumentContent convertDocument(final Path filePath, final String fileName) {
        if (filePath == null || !Files.exists(filePath)) {
            return DocumentContent.empty();
        }

        try {
            String boundary = "xiyu-compliance-" + UUID.randomUUID().toString().replace("-", "");
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(sidecarUrl + "/convert"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary);

            if (!sharedKey.isBlank()) {
                requestBuilder.header(SIDECAR_KEY_HEADER, sharedKey);
            }

            HttpRequest request = requestBuilder
                    .POST(HttpRequest.BodyPublishers.concat(
                            HttpRequest.BodyPublishers.ofByteArray(multipartPrefix(boundary, fileName)),
                            HttpRequest.BodyPublishers.ofFile(filePath),
                            HttpRequest.BodyPublishers.ofByteArray(multipartSuffix(boundary))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String markdown = root.path("markdown").asText("");
                String contentHash = root.path("contentHash").asText("");
                if (contentHash.isBlank()) {
                    contentHash = computeSha256(filePath);
                }
                return buildDocumentContent(fileName, contentHash, markdown);
            } else {
                log.warn("Sidecar /convert 返回状态码 {}: {}, 降级使用本地哈希与文本", response.statusCode(), response.body());
            }
        } catch (java.io.IOException | InterruptedException | RuntimeException e) {
            log.warn("调用文档转换 Sidecar 异常，采用本地降级解析: {}", e.getMessage());
        }

        // 降级处理：读取文本或计算本地哈希
        try {
            String contentHash = computeSha256(filePath);
            String localText = Files.readString(filePath, StandardCharsets.UTF_8);
            return buildDocumentContent(fileName, contentHash, localText);
        } catch (java.io.IOException | RuntimeException e) {
            log.warn("读取本地降级文件失败: {}", e.getMessage());
            String hash = computeSha256(filePath);
            return new DocumentContent(fileName, hash, "", Collections.emptyList());
        }
    }

    /**
     * 将纯文本与哈希封装为 DocumentContent.
     */
    public DocumentContent buildDocumentContent(
            final String fileName, final String fileHash, final String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return new DocumentContent(fileName, fileHash, "", Collections.emptyList());
        }

        // 识别物理分页符 \f
        String[] rawPages = fullText.split("\f");
        List<DocumentContent.PageContent> pages = new ArrayList<>();
        for (int i = 0; i < rawPages.length; i++) {
            String pageText = rawPages[i].trim();
            if (!pageText.isEmpty()) {
                pages.add(new DocumentContent.PageContent(i + 1, pageText, Collections.emptyList()));
            }
        }
        if (pages.isEmpty()) {
            pages.add(new DocumentContent.PageContent(1, fullText, Collections.emptyList()));
        }
        return new DocumentContent(fileName, fileHash, fullText, pages);
    }

    public static String computeSha256(final Path path) {
        if (path == null || !Files.exists(path)) {
            return "";
        }
        try (InputStream is = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
            byte[] hash = digest.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.io.IOException | java.security.NoSuchAlgorithmException | RuntimeException e) {
            return "";
        }
    }

    public static String computeStringSha256(final String text) {
        if (text == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException | RuntimeException e) {
            return "";
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
