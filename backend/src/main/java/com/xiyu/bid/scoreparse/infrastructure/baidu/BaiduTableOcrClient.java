// Input: PDF file bytes and page numbers
// Output: SidecarTableExtraction and raw Baidu OCR table response
// Pos: scoreparse/infrastructure/baidu - Baidu Table OCR client
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    private final BaiduOcrCredentialsProvider credentialsProvider;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    private record CachedToken(String token, String apiKey, Instant expireAt) {
        boolean isValid(String currentApiKey) {
            return token != null
                    && Objects.equals(apiKey, currentApiKey)
                    && Instant.now().isBefore(expireAt.minusSeconds(600)); // 提前10分钟刷新
        }
    }

    public BaiduTableOcrClient(BaiduOcrProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, null);
    }

    @Autowired
    public BaiduTableOcrClient(
            BaiduOcrProperties properties,
            ObjectMapper objectMapper,
            @Autowired(required = false) BaiduOcrCredentialsProvider credentialsProvider
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.credentialsProvider = credentialsProvider;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build();
    }

    public boolean isEnabled() {
        if (credentialsProvider != null) {
            return credentialsProvider.isEnabled();
        }
        return properties != null && properties.isEnabled();
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
        String apiKey = (credentialsProvider != null && credentialsProvider.getApiKey() != null && !credentialsProvider.getApiKey().isBlank())
                ? credentialsProvider.getApiKey() : properties.getApiKey();
        String secretKey = (credentialsProvider != null && credentialsProvider.getSecretKey() != null && !credentialsProvider.getSecretKey().isBlank())
                ? credentialsProvider.getSecretKey() : properties.getSecretKey();
        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new IllegalStateException("Baidu OCR API Key or Secret Key is not configured.");
        }

        CachedToken current = tokenCache.get();
        if (current != null && current.isValid(apiKey)) {
            return current.token();
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
        tokenCache.set(new CachedToken(tokenResponse.accessToken(), apiKey, expireAt));
        log.info("Baidu OCR access token refreshed successfully, expires in {}s", expiresIn);
        return tokenResponse.accessToken();
    }
}
