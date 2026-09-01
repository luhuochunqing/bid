// Input: 临时文件路径 + 文件名（Sidecar 响应：markdown + 可选结构化 pages 物理页）
// Output: 结构化 DocumentContent（Markdown、分页优先级 pages→\f→虚拟分页、SHA-256 哈希，含安全降级守卫）
// Pos: compliance/service/ - 标书文档转换 Sidecar 流式适配器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
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

        String rawContentHash = computeSha256(filePath);
        Path uploadFilePath = filePath;
        boolean needsCleanup = false;

        try {
            if (DocxMediaFilter.shouldFilter(filePath, fileName, DocxMediaFilter.DEFAULT_THRESHOLD_BYTES)) {
                Path slimPath = DocxMediaFilter.filterMediaToTempFile(filePath);
                if (slimPath != null && !slimPath.equals(filePath)) {
                    uploadFilePath = slimPath;
                    needsCleanup = true;
                }
            }

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
                            HttpRequest.BodyPublishers.ofFile(uploadFilePath),
                            HttpRequest.BodyPublishers.ofByteArray(multipartSuffix(boundary))))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                JsonNode root = objectMapper.readTree(response.body());
                String contentHash = rawContentHash;
                if (contentHash == null || contentHash.isBlank()) {
                    contentHash = root.path("contentHash").asText("");
                }
                return documentFromSidecarPayload(fileName, contentHash, root);
            } else {
                log.warn("Sidecar /convert 返回状态码 {}: {}, 降级使用本地哈希与文本", response.statusCode(), response.body());
            }
        } catch (Throwable t) {
            log.warn("调用文档转换 Sidecar 异常，采用本地降级解析: {}", t.getMessage());
        } finally {
            if (needsCleanup && uploadFilePath != null) {
                try {
                    Files.deleteIfExists(uploadFilePath);
                } catch (IOException e) {
                    log.warn("清理 Docx 瘦身临时文件失败 {}: {}", uploadFilePath, e.getMessage());
                }
            }
        }

        // 降级处理：仅对纯文本且小文件（<=5MB）尝试本地读取；
        // 对 .docx / .pdf / .zip 等二进制文件或大文件，绝不调用 Files.readString，直接返回空文本以避免堆外 DirectBuffer OOM
        try {
            String contentHash = (rawContentHash != null && !rawContentHash.isBlank()) ? rawContentHash : computeSha256(filePath);
            if (isSmallPlainTextFile(fileName, filePath)) {
                String localText = Files.readString(filePath, StandardCharsets.UTF_8);
                return buildDocumentContent(fileName, contentHash, localText);
            }
            log.info("文件 ({}) 为二进制或超大文件，跳过本地文本读取降级，仅保留哈希", fileName);
            return new DocumentContent(fileName, contentHash, "", Collections.emptyList());
        } catch (Throwable t) {
            log.warn("读取本地降级文件失败: {}", t.getMessage());
            String hash = (rawContentHash != null && !rawContentHash.isBlank()) ? rawContentHash : computeSha256(filePath);
            return new DocumentContent(fileName, hash, "", Collections.emptyList());
        }
    }

    private static final java.util.Set<String> PLAIN_TEXT_EXTENSIONS = java.util.Set.of(
            ".txt", ".md", ".markdown", ".csv", ".json", ".xml", ".html", ".htm"
    );
    private static final long MAX_LOCAL_READ_BYTES = 5 * 1024 * 1024; // 5MB

    private boolean isSmallPlainTextFile(final String fileName, final Path filePath) {
        if (fileName == null || filePath == null) {
            return false;
        }
        String lowerName = fileName.toLowerCase();
        boolean isPlainText = PLAIN_TEXT_EXTENSIONS.stream().anyMatch(lowerName::endsWith);
        if (!isPlainText) {
            return false;
        }
        try {
            return Files.size(filePath) <= MAX_LOCAL_READ_BYTES;
        } catch (Throwable t) {
            return false;
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
        // 分页缺失兜底：markitdown 输出无 \f 时整篇退化为单页，
        // 长文本按标题/段落虚拟分页，恢复页数判断、证据定位与检索预算的有效性
        if (pages.size() == 1 && fullText.length() > DocumentContent.VIRTUAL_PAGE_MIN_TEXT_LENGTH) {
            pages = DocumentContent.virtualPages(fullText);
        }
        return new DocumentContent(fileName, fileHash, fullText, pages);
    }

    /**
     * 解析 Sidecar /convert 响应为 DocumentContent：
     * 优先消费结构化 pages（PDF 物理页）；缺省时回退 {@link #buildDocumentContent}
     * 的 \f 切分与虚拟分页兜底.
     */
    DocumentContent documentFromSidecarPayload(
            final String fileName, final String contentHash, final JsonNode payload) {
        String markdown = payload.path("markdown").asText("");
        JsonNode pagesNode = payload.path("pages");
        if (pagesNode.isArray() && pagesNode.size() > 0) {
            List<DocumentContent.PageContent> pages = new ArrayList<>();
            for (int i = 0; i < pagesNode.size(); i++) {
                JsonNode pageNode = pagesNode.get(i);
                String pageText = pageNode.path("text").asText("");
                if (pageText.isBlank()) {
                    continue;
                }
                int pageNumber = Math.max(1, pageNode.path("pageNumber").asInt(pages.size() + 1));
                pages.add(new DocumentContent.PageContent(pageNumber, pageText, Collections.emptyList()));
            }
            if (!pages.isEmpty()) {
                return new DocumentContent(fileName, contentHash, markdown, pages);
            }
        }
        return buildDocumentContent(fileName, contentHash, markdown);
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
