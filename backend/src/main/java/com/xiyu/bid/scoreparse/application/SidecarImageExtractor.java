// Input: OnDisk Path（流式 ofFile）或 InMemory byte[]（ofByteArray 直传）+ 文件名
// Output: SidecarImageExtraction(images, truncated)；响应整包超限抛 OversizedImageResponseException
// Pos: scoreparse/application — /convert-images 客户端（spec 048 T012，契约 contracts/sidecar-images-api.md）
package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
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
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * sidecar /convert-images 客户端（spec 048）。
 * <p>双入口：OnDisk 临时文件流式上传（ofFile，零整包请求内存，同 SidecarStreamTextExtractor）；
 * InMemory 全量 byte[] 直传（ofByteArray，review H2——内存中已有整包，再走临时文件只增加 IO）。
 * 响应整包读 + 字节计数，累计超 max-extract-response-bytes(25MB) 抛 OversizedImageResponseException。
 * 逐项防御式过滤非法 mimeType / 空 base64（Principle VII），不因单项脏数据丢弃整批图片。
 */
@Component
@Slf4j
public class SidecarImageExtractor {

    private static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";
    private static final int ERROR_SNIPPET_MAX_CHARS = 512;
    private static final Set<String> SUPPORTED_MIME_TYPES =
            Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    private final HttpClient httpClient;
    private final String sidecarUrl;
    private final String sharedKey;
    private final long maxResponseBytes;
    private final long extractTimeoutSeconds;
    private final ObjectMapper objectMapper;

    public SidecarImageExtractor(
            @Value("${app.score-parse.sidecar-url:http://localhost:8000}") String sidecarUrl,
            @Value("${app.score-parse.sidecar-shared-key:${SIDECAR_SHARED_KEY:}}") String sharedKey,
            @Value("${app.score-parse.multimodal.max-extract-response-bytes:26214400}") long maxResponseBytes,
            @Value("${app.score-parse.bid-file.extract-timeout-seconds:600}") long extractTimeoutSeconds,
            ObjectMapper objectMapper) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.sidecarUrl = sidecarUrl.endsWith("/")
                ? sidecarUrl.substring(0, sidecarUrl.length() - 1)
                : sidecarUrl;
        this.sharedKey = sharedKey == null ? "" : sharedKey.trim();
        this.maxResponseBytes = maxResponseBytes;
        this.extractTimeoutSeconds = extractTimeoutSeconds;
        this.objectMapper = objectMapper;
    }

    /** OnDisk 路径：磁盘临时文件流式上传（ofFile）。 */
    public SidecarImageExtraction extract(Path file, String fileName)
            throws IOException, InterruptedException {
        return post(HttpRequest.BodyPublishers.ofFile(file), fileName);
    }

    /** InMemory 路径：全量 byte[] 直传（ofByteArray）。 */
    public SidecarImageExtraction extract(byte[] content, String fileName)
            throws IOException, InterruptedException {
        return post(HttpRequest.BodyPublishers.ofByteArray(content), fileName);
    }

    private SidecarImageExtraction post(HttpRequest.BodyPublisher fileBody, String fileName)
            throws IOException, InterruptedException {
        String boundary = "xiyu-score-parse-images-" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(sidecarUrl + "/convert-images"))
                .timeout(Duration.ofSeconds(extractTimeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!sharedKey.isBlank()) {
            requestBuilder.header(SIDECAR_KEY_HEADER, sharedKey);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(multipartPrefix(boundary, fileName)),
                        fileBody,
                        HttpRequest.BodyPublishers.ofByteArray(multipartSuffix(boundary))))
                .build();
        HttpResponse<InputStream> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
        int status = response.statusCode();
        if (status == 422) {
            throw new IllegalStateException("投标文件图片无法解析：" + readErrorSnippet(response.body()));
        }
        if (status < 200 || status >= 300) {
            throw new IllegalStateException(
                    "调用文档图片抽取服务失败 HTTP " + status + "：" + readErrorSnippet(response.body()));
        }
        try (InputStream body = response.body()) {
            return parseResponse(readBodyWithByteLimit(body));
        }
    }

    private SidecarImageExtraction parseResponse(byte[] bodyBytes) throws IOException {
        ResponsePayload payload = objectMapper.readValue(bodyBytes, ResponsePayload.class);
        if (payload == null || payload.images == null) {
            return SidecarImageExtraction.empty();
        }
        List<DocumentImage> images = new ArrayList<>();
        for (ImagePayload item : payload.images) {
            if (item == null || !SUPPORTED_MIME_TYPES.contains(item.mimeType)) {
                log.warn("跳过非法图片项（mimeType 不支持）: {}", item == null ? "null" : item.mimeType);
                continue;
            }
            if (item.base64 == null || item.base64.isBlank()) {
                log.warn("跳过非法图片项（base64 为空）: index={}", item.index);
                continue;
            }
            images.add(new DocumentImage(
                    item.index, item.mimeType, item.section, item.pageHint,
                    item.width, item.height, item.base64));
        }
        return new SidecarImageExtraction(images, payload.truncated);
    }

    /** 流式读响应 + 字节计数：累计超限即抛（关闭 body 中止连接），不先缓冲完整响应再判。 */
    private byte[] readBodyWithByteLimit(InputStream body) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        long totalBytes = 0;
        int n;
        while ((n = body.read(buf)) != -1) {
            totalBytes += n;
            if (totalBytes > maxResponseBytes) {
                log.warn("图片抽取响应超限(extract-images): totalBytes={}, maxResponseBytes={}",
                        totalBytes, maxResponseBytes);
                throw new OversizedImageResponseException();
            }
            buffer.write(buf, 0, n);
        }
        return buffer.toByteArray();
    }

    private static byte[] multipartPrefix(String boundary, String fileName) {
        return ("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] multipartSuffix(String boundary) {
        return ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
    }

    private static String readErrorSnippet(InputStream body) {
        try (body) {
            byte[] buf = new byte[ERROR_SNIPPET_MAX_CHARS];
            int n = body.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            return "";
        }
    }

    /** sidecar /convert-images 响应 DTO（契约 contracts/sidecar-images-api.md）。 */
    private static final class ResponsePayload {
        public List<ImagePayload> images;
        public boolean truncated;
        public List<String> warnings;
    }

    private static final class ImagePayload {
        public int index;
        public String mimeType;
        public String section;
        public Integer pageHint;
        public int width;
        public int height;
        public String base64;
    }
}
