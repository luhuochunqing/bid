// Input: 文件字节数组 byte[] / Base64 数据 + 文件名
// Output: 百度 PaddleOCR-VL 解析出的增强版面 Markdown 字符串（Optional<String>，支持优雅降级）
// Pos: compliance/service/ - 百度 PaddleOCR-VL 文档解析客户端
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 百度智能云 PaddleOCR-VL 文档解析服务客户端.
 */
@Component
@Slf4j
public class BaiduPaddleVlClient {

    public record Config(boolean enabled, String apiKey, String secretKey, long timeoutSeconds) {
        public boolean isConfigured() {
            return enabled && apiKey != null && !apiKey.isBlank() && secretKey != null && !secretKey.isBlank();
        }
    }

    private record TokenHolder(String token, Instant expiry) {}

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String TASK_SUBMIT_URL = "https://aip.baidubce.com/rest/2.0/brain/online/v2/paddle-vl-parser/task";
    private static final String TASK_QUERY_URL = "https://aip.baidubce.com/rest/2.0/brain/online/v2/paddle-vl-parser/task/query";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Config config;
    private final AtomicReference<TokenHolder> tokenHolder = new AtomicReference<>();

    @Autowired
    public BaiduPaddleVlClient(
            ObjectMapper objectMapper,
            @Value("${app.baidu-ocr.enabled:true}") boolean enabled,
            @Value("${app.baidu-ocr.api-key:}") String apiKey,
            @Value("${app.baidu-ocr.secret-key:}") String secretKey,
            @Value("${app.baidu-ocr.timeout-seconds:30}") long timeoutSeconds) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                objectMapper != null ? objectMapper : new ObjectMapper(),
                new Config(enabled, apiKey != null ? apiKey.trim() : "", secretKey != null ? secretKey.trim() : "",
                        timeoutSeconds > 0 ? timeoutSeconds : 30));
    }

    public BaiduPaddleVlClient(HttpClient httpClient, ObjectMapper objectMapper, Config config) {
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
        this.config = config != null ? config : new Config(false, "", "", 30);
    }

    public boolean isConfigured() {
        return config.isConfigured();
    }

    public Optional<String> parseDocument(final byte[] fileBytes, final String fileName) {
        if (!isConfigured() || fileBytes == null || fileBytes.length == 0 || fileName == null) {
            return Optional.empty();
        }
        try {
            String token = getAccessToken();
            if (token == null || token.isBlank()) {
                return Optional.empty();
            }
            String base64Data = Base64.getEncoder().encodeToString(fileBytes);
            String submitBody = "file_data=" + URLEncoder.encode(base64Data, StandardCharsets.UTF_8)
                    + "&file_name=" + URLEncoder.encode(fileName, StandardCharsets.UTF_8);

            HttpRequest submitRequest = HttpRequest.newBuilder()
                    .uri(URI.create(TASK_SUBMIT_URL + "?access_token=" + token))
                    .timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(submitBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> submitResponse = httpClient.send(submitRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (submitResponse.statusCode() != 200) {
                log.warn("百度 PaddleOCR-VL 提交失败: HTTP {}", submitResponse.statusCode());
                return Optional.empty();
            }

            JsonNode submitJson = objectMapper.readTree(submitResponse.body());
            if (submitJson.path("error_code").asInt(0) != 0) {
                log.warn("百度 PaddleOCR-VL 业务错误: {}", submitJson.path("error_msg").asText());
                return Optional.empty();
            }

            String taskId = submitJson.path("result").path("task_id").asText();
            return taskId.isBlank() ? Optional.empty() : pollTaskResult(token, taskId);
        } catch (Throwable t) {
            log.warn("调用百度 PaddleOCR-VL 异常: {}", t.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> pollTaskResult(final String token, final String taskId) throws IOException, InterruptedException {
        String queryBody = "task_id=" + URLEncoder.encode(taskId, StandardCharsets.UTF_8);
        long deadline = System.currentTimeMillis() + config.timeoutSeconds() * 1000L;

        while (System.currentTimeMillis() < deadline) {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(TASK_QUERY_URL + "?access_token=" + token))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(queryBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonNode queryJson = objectMapper.readTree(resp.body());
                String status = queryJson.path("result").path("status").asText();
                if ("success".equalsIgnoreCase(status)) {
                    String url = queryJson.path("result").path("markdown_url").asText();
                    return url.isBlank() ? Optional.empty() : downloadMarkdown(url);
                } else if ("failed".equalsIgnoreCase(status)) {
                    log.warn("百度 PaddleOCR-VL 解析失败: taskId={}", taskId);
                    return Optional.empty();
                }
            }
            Thread.sleep(1000);
        }
        return Optional.empty();
    }

    private Optional<String> downloadMarkdown(final String markdownUrl) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(markdownUrl)).timeout(Duration.ofSeconds(15)).GET().build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return resp.statusCode() == 200 ? Optional.ofNullable(resp.body()) : Optional.empty();
        } catch (Throwable t) {
            log.warn("下载百度 OCR Markdown 失败: {}", t.getMessage());
            return Optional.empty();
        }
    }

    private synchronized String getAccessToken() {
        TokenHolder current = tokenHolder.get();
        if (current != null && Instant.now().isBefore(current.expiry())) {
            return current.token();
        }
        try {
            String url = TOKEN_URL + "?grant_type=client_credentials&client_id="
                    + URLEncoder.encode(config.apiKey(), StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(config.secretKey(), StandardCharsets.UTF_8);

            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(10)).POST(HttpRequest.BodyPublishers.noBody()).build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String token = root.path("access_token").asText();
                int expiresIn = root.path("expires_in").asInt(2592000);
                if (!token.isBlank()) {
                    tokenHolder.set(new TokenHolder(token, Instant.now().plusSeconds(Math.max(60, expiresIn - 300))));
                    return token;
                }
            }
        } catch (Throwable t) {
            log.warn("请求百度 AccessToken 异常: {}", t.getMessage());
        }
        return null;
    }
}
