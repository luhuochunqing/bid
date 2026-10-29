// Input: PDF file bytes and page numbers
// Output: SidecarTableExtraction and raw Baidu OCR table response (with in-memory single-page slicing optimization)
// Pos: scoreparse/infrastructure/baidu - Baidu Table OCR client
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTokenResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
     * <p>对多页 PDF 执行智能单页切片，仅将目标单页抽离后做 Base64 传输，避免全量大文件反复传输导致的带宽浪费与内存雪崩。
     */
    public BaiduTableResponse extractTablesFromPdf(byte[] pdfBytes, Integer pageNum) throws IOException, InterruptedException {
        Objects.requireNonNull(pdfBytes, "pdfBytes must not be null");
        String accessToken = getAccessToken();

        byte[] uploadBytes = pdfBytes;
        Integer uploadPageNum = pageNum;
        if (pageNum != null && pageNum > 0) {
            byte[] sliced = slicePdfPage(pdfBytes, pageNum);
            if (sliced != pdfBytes && sliced.length > 0) {
                uploadBytes = sliced;
                uploadPageNum = 1; // 单页切片后文档仅有第 1 页
                if (log.isInfoEnabled()) {
                    double reductionRate = (1.0 - (double) uploadBytes.length / pdfBytes.length) * 100.0;
                    log.info("BaiduTableOcrClient: Sliced page {} into single-page PDF (payload: {} -> {} bytes, -{}.1f%%)",
                            pageNum, pdfBytes.length, uploadBytes.length, (int) reductionRate);
                }
            }
        }

        String pdfBase64 = Base64.getEncoder().encodeToString(uploadBytes);
        StringBuilder formBody = new StringBuilder();
        formBody.append("pdf_file=").append(URLEncoder.encode(pdfBase64, StandardCharsets.UTF_8));
        if (uploadPageNum != null && uploadPageNum > 0) {
            formBody.append("&pdf_file_num=").append(uploadPageNum);
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

    /**
     * 内存单页切片: 从完整 PDF 中抽取目标单页为独立的微型单页 PDF 字节数组。
     * <p>大幅降低传输体积与堆内存占用，避免多页大文件全量 Base64 循环编码导致的雪崩。
     *
     * @param pdfBytes 完整 PDF 二进制数据
     * @param pageNum 目标物理页码 (从 1 开始)
     * @return 抽取的单页 PDF 二进制数据; 若文档本身仅 1 页或发生边界异常，则安全降级返回原始 pdfBytes
     */
    public static byte[] slicePdfPage(byte[] pdfBytes, int pageNum) {
        if (pdfBytes == null || pdfBytes.length == 0 || pageNum <= 0) {
            return pdfBytes;
        }
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdfBytes))) {
            int totalPages = doc.getNumberOfPages();
            if (totalPages <= 1) {
                return pdfBytes;
            }
            if (pageNum > totalPages) {
                log.warn("slicePdfPage: requested pageNum {} exceeds totalPages {}, fallback to original bytes", pageNum, totalPages);
                return pdfBytes;
            }
            try (PDDocument singlePageDoc = new PDDocument()) {
                singlePageDoc.addPage(doc.getPage(pageNum - 1));
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                singlePageDoc.save(baos);
                return baos.toByteArray();
            }
        } catch (IOException | RuntimeException e) {
            log.warn("slicePdfPage: Failed to slice single page {} from PDF (size={}), fallback to original bytes: {}",
                    pageNum, pdfBytes.length, e.getMessage());
            return pdfBytes;
        }
    }
}
