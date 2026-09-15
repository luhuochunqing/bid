// Input: 临时文件 Path + 文件名（OnDisk 路径，LoadedBidFile.OnDisk）
// Output: 提取后的 markdown 纯文本；char 超限抛 OversizedBidTextException
// Pos: scoreparse/application — /convert-stream 流式客户端（spec 045 T009，契约 contracts/sidecar-streaming-api.md）
package com.xiyu.bid.scoreparse.application;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;

/**
 * sidecar /convert-stream 客户端：磁盘临时文件流式上传（ofFile，零整包内存），
 * 响应流式读 + char 计数，累计 > max-text-chars(500万) 中止连接抛
 * OversizedBidTextException（FR-004），否则拼装文本返回。
 * <p>与 docinsight 的 MarkItDownSidecarExtractor 互不相干：本类只服务打分链路。
 */
@Component
@Slf4j
public class SidecarStreamTextExtractor {

    private static final int ERROR_SNIPPET_MAX_CHARS = 512;
    private static final int MAX_ATTEMPTS = 3;
    private static final long RETRY_BACKOFF_MILLIS = 250L;

    private final HttpClient httpClient;
    private final String sidecarUrl;
    private final String sharedKey;
    private final long maxTextChars;
    private final long extractTimeoutSeconds;

    public SidecarStreamTextExtractor(
            // 默认值与部署现实对齐：测试/生产 sidecar 容器映射 host 8000（同 doc-insight 两个提取器）；
            // 本地开发（dev-services.sh，端口 8009）由 application-dev.yml 覆盖
            @Value("${app.score-parse.sidecar-url:http://localhost:8000}") String sidecarUrl,
            @Value("${app.score-parse.sidecar-shared-key:${SIDECAR_SHARED_KEY:}}") String sharedKey,
            @Value("${app.score-parse.bid-file.max-text-chars:5000000}") long maxTextChars,
            @Value("${app.score-parse.bid-file.extract-timeout-seconds:600}") long extractTimeoutSeconds) {
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        this.sidecarUrl = sidecarUrl.endsWith("/")
                ? sidecarUrl.substring(0, sidecarUrl.length() - 1)
                : sidecarUrl;
        this.sharedKey = sharedKey == null ? "" : sharedKey.trim();
        this.maxTextChars = maxTextChars;
        this.extractTimeoutSeconds = extractTimeoutSeconds;
    }

    public String extract(Path file, String fileName) throws IOException, InterruptedException {
        String boundary = "xiyu-score-parse-" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(sidecarUrl + "/convert-stream"))
                .timeout(Duration.ofSeconds(extractTimeoutSeconds))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (!sharedKey.isBlank()) {
            requestBuilder.header(SidecarMultipart.SIDECAR_KEY_HEADER, sharedKey);
        }
        HttpRequest request = requestBuilder
                .POST(HttpRequest.BodyPublishers.concat(
                        HttpRequest.BodyPublishers.ofByteArray(SidecarMultipart.prefix(boundary, fileName)),
                        HttpRequest.BodyPublishers.ofFile(file),
                        HttpRequest.BodyPublishers.ofByteArray(SidecarMultipart.suffix(boundary))))
                .build();
        IOException lastIoFailure = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpResponse<InputStream> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status == 422) {
                    throw new IllegalStateException("投标文件无法解析：" + readErrorSnippet(response.body()));
                }
                if (status < 200 || status >= 300) {
                    String detail = readErrorSnippet(response.body());
                    if (isRetryableStatus(status) && attempt < MAX_ATTEMPTS) {
                        sleepBeforeRetry(attempt);
                        continue;
                    }
                    throw new IllegalStateException("调用文档转换服务失败 HTTP " + status + "：" + detail);
                }
                try (InputStream body = response.body()) {
                    return readTextWithCharLimit(body);
                }
            } catch (IOException ex) {
                lastIoFailure = ex;
                if (attempt == MAX_ATTEMPTS) {
                    throw new IOException("调用文档转换服务失败，已重试 " + MAX_ATTEMPTS
                            + " 次：" + describe(ex), ex);
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw new IOException("调用文档转换服务失败：" + describe(lastIoFailure), lastIoFailure);
    }

    private static boolean isRetryableStatus(int status) {
        return status == 502 || status == 503 || status == 504;
    }

    private static void sleepBeforeRetry(int attempt) throws InterruptedException {
        Thread.sleep(RETRY_BACKOFF_MILLIS * attempt);
    }

    private static String describe(Throwable error) {
        if (error == null || error.getMessage() == null || error.getMessage().isBlank()) {
            return error == null ? "未知异常" : error.getClass().getSimpleName();
        }
        return error.getClass().getSimpleName() + ": " + error.getMessage();
    }

    /** 流式读响应 + char 计数：累计超限即抛（关闭 body 中止连接），不先缓冲完整文本再判。 */
    private String readTextWithCharLimit(InputStream body) throws IOException {
        StringBuilder text = new StringBuilder();
        Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8);
        char[] buf = new char[8192];
        long totalChars = 0;
        int n;
        while ((n = reader.read(buf)) != -1) {
            totalChars += n;
            if (totalChars > maxTextChars) {
                log.warn("投标文件文本量超限(extract): totalChars={}, maxTextChars={}", totalChars, maxTextChars);
                throw new OversizedBidTextException();
            }
            text.append(buf, 0, n);
        }
        return text.toString();
    }

    private static String readErrorSnippet(InputStream body) {
        try (body) {
            Reader reader = new InputStreamReader(body, StandardCharsets.UTF_8);
            char[] buf = new char[ERROR_SNIPPET_MAX_CHARS];
            int n = reader.read(buf);
            return n <= 0 ? "" : new String(buf, 0, n).trim();
        } catch (IOException ex) {
            return "";
        }
    }
}
