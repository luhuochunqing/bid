package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTokenResponse;
import lombok.extern.slf4j.Slf4j;
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
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 百度智能云表格识别（Table OCR）客户端组件。
 * <p>支持自动维护 OAuth2 access_token 缓存生命周期，执行 PDF 表格识别。
 */
@Slf4j
@Component
public class BaiduTableOcrClient {

    private final BaiduOcrProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    private record CachedToken(String token, Instant expireAt) {
        boolean isValid() {
            return token != null && Instant.now().isBefore(expireAt.minusSeconds(600)); // 提前10分钟刷新
        }
    }

    public BaiduTableOcrClient(BaiduOcrProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    /**
     * 提取 PDF 指定页的表格，直接返回系统内部的 SidecarTableExtraction。
     */
    public SidecarTableExtraction extractSidecarTables(byte[] pdfBytes, Integer pageNum) throws IOException, InterruptedException {
        BaiduTableResponse response = extractTablesFromPdf(pdfBytes, pageNum);
        return BaiduTableArtifactAdapter.toSidecarTableExtraction(response);
    }

    /**
     * 核心接口：调用百度表格识别 API。
     */
    public BaiduTableResponse extractTablesFromPdf(byte[] pdfBytes, Integer pageNum) throws IOException, InterruptedException {
        Objects.requireNonNull(pdfBytes, "pdfBytes must not be null");
        String accessToken = getAccessToken();

        String pdfBase64 = Base64.getEncoder().encodeToString(pdfBytes);
        StringBuilder formBody = new StringBuilder();
        formBody.append("pdf_file=").append(URLEncoder.encode(pdfBase64, StandardCharsets.UTF_8));
        if (pageNum != null && pageNum > 0) {
            formBody.append("&pdf_file_num=").append(pageNum);
        }

        String requestUrl = properties.getTableUrl() + "?access_token=" + accessToken;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(requestUrl))
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody.toString()))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.error("Baidu Table OCR HTTP failed: status={}, body={}", response.statusCode(), response.body());
            throw new IOException("Baidu OCR HTTP error: " + response.statusCode());
        }

        BaiduTableResponse tableResponse = objectMapper.readValue(response.body(), BaiduTableResponse.class);
        if (!tableResponse.isSuccess()) {
            log.warn("Baidu Table OCR returned business error: code={}, msg={}", tableResponse.errorCode(), tableResponse.errorMsg());
        }
        return tableResponse;
    }

    /**
     * 获取有效的 AccessToken，优先读内存缓存，失效时自动刷新。
     */
    public synchronized String getAccessToken() throws IOException, InterruptedException {
        CachedToken current = tokenCache.get();
        if (current != null && current.isValid()) {
            return current.token();
        }

        String apiKey = properties.getApiKey();
        String secretKey = properties.getSecretKey();
        if (apiKey.isBlank() || secretKey.isBlank()) {
            throw new IllegalStateException("Baidu OCR API Key or Secret Key is not configured.");
        }

        String tokenUrl = String.format("%s?grant_type=client_credentials&client_id=%s&client_secret=%s",
                properties.getTokenUrl(),
                URLEncoder.encode(apiKey, StandardCharsets.UTF_8),
                URLEncoder.encode(secretKey, StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .timeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            log.error("Failed to fetch Baidu access token: status={}, body={}", response.statusCode(), response.body());
            throw new IOException("Failed to fetch Baidu access token: HTTP " + response.statusCode());
        }

        BaiduTokenResponse tokenResponse = objectMapper.readValue(response.body(), BaiduTokenResponse.class);
        if (tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
            log.error("Baidu token error: error={}, desc={}", tokenResponse.error(), tokenResponse.errorDescription());
            throw new IOException("Baidu token error: " + tokenResponse.errorDescription());
        }

        long expiresIn = tokenResponse.expiresIn() != null ? tokenResponse.expiresIn() : 2592000L;
        Instant expireAt = Instant.now().plusSeconds(expiresIn);
        tokenCache.set(new CachedToken(tokenResponse.accessToken(), expireAt));
        log.info("Baidu OCR access token refreshed successfully, expires in {}s", expiresIn);
        return tokenResponse.accessToken();
    }
}
