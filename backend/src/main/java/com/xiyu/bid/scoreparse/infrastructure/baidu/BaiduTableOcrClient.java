// Input: PDF file bytes and page numbers
// Output: Validated APIHub OCR results with deployment gating, cached login and single-page PDF slicing
// Pos: scoreparse/infrastructure/baidu - Baidu Table OCR client
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.scoreparse.infrastructure.baidu;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.SidecarTableExtraction;
import com.xiyu.bid.scoreparse.infrastructure.baidu.BaiduTableModels.BaiduTableResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 百度智能云表格识别（Table OCR）客户端组件。
 * <p>通过 APIHub 登录和 satoken 鉴权执行 PDF 表格识别。
 */
@Slf4j
@Component
public class BaiduTableOcrClient {

    private final BaiduOcrProperties properties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final BaiduOcrCredentialsProvider credentialsProvider;

    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    private record CachedToken(String token, String username, String password, Instant expireAt) {
        boolean isValid(String currentUsername, String currentPassword) {
            return Objects.equals(username, currentUsername)
                    && Objects.equals(password, currentPassword)
                    && Instant.now().isBefore(expireAt);
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
        if (properties == null || !properties.isGatewayEnabled()) {
            return false;
        }
        if (credentialsProvider != null) {
            return credentialsProvider.isEnabled();
        }
        return properties.isEnabled();
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
        Map<String, String> params = new LinkedHashMap<>();
        params.put("pdf_file", pdfBase64);
        if (uploadPageNum != null && uploadPageNum > 0) {
            params.put("pdf_file_num", uploadPageNum.toString());
        }
        String body = objectMapper.writeValueAsString(Map.of(
                "code", "BAIDU_OCR_DOC_API", "noCache", 1,
                "params", objectMapper.writeValueAsString(params)));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTableUrl()))
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("satoken", accessToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            if (response.statusCode() == 401 || response.statusCode() == 403) tokenCache.set(null);
            throw new IOException("APIHub OCR HTTP error: " + response.statusCode());
        }

        return parseTableResponse(response.body());
    }

    BaiduTableResponse parseTableResponse(String body) throws IOException {
        JsonNode envelope = readEnvelope(body, "search");
        if (envelope.path("data").path("isSuccess").asInt() != 1) {
            throw new IOException("APIHub OCR upstream call failed");
        }
        JsonNode result = envelope.path("data").path("result");
        if (result.isTextual()) result = objectMapper.readTree(result.asText());
        validateTableResult(result);
        BaiduTableResponse tableResponse = objectMapper.treeToValue(result, BaiduTableResponse.class);
        if (!tableResponse.isSuccess()) {
            log.warn("Baidu Table OCR returned business error: code={}, msg={}", tableResponse.errorCode(), tableResponse.errorMsg());
        }
        return tableResponse;
    }

    private static void validateTableResult(JsonNode result) throws IOException {
        if (result == null || !result.isObject()) {
            throw new IOException("APIHub OCR result is missing or invalid");
        }
        JsonNode error = result.get("error_code");
        if (error != null && (!error.isIntegralNumber() || !error.canConvertToInt())) {
            throw new IOException("APIHub OCR error code is invalid");
        }
        if (error != null && error.intValue() != 0) return;
        validateTableCount(result);
    }

    private static void validateTableCount(JsonNode result) throws IOException {
        JsonNode count = result.path("table_num");
        if (!count.isIntegralNumber() || !count.canConvertToInt() || count.intValue() < 0) {
            throw new IOException("APIHub OCR table count is missing or invalid");
        }
        JsonNode tables = result.get("tables_result");
        if (tables == null && count.intValue() == 0) return;
        if (tables == null || !tables.isArray() || tables.size() != count.intValue()) {
            throw new IOException("APIHub OCR table results do not match table count");
        }
        for (JsonNode table : tables) {
            if (!table.isObject()) throw new IOException("APIHub OCR table item is invalid");
        }
    }

    /** 获取有效 token；同步协调凭据变更、缓存读取与刷新。 */
    public synchronized String getAccessToken() throws IOException, InterruptedException {
        if (!properties.isGatewayEnabled()) {
            throw new IllegalStateException("OCR gateway is not enabled for this deployment; test environments only.");
        }
        var credentials = resolveLoginCredentials();
        CachedToken current = tokenCache.get();
        if (current != null && current.isValid(credentials.username(), credentials.password())) return current.token();
        CachedToken refreshed = requestLogin(credentials);
        tokenCache.set(refreshed);
        return refreshed.token();
    }

    private record LoginCredentials(String username, String password) {}

    private LoginCredentials resolveLoginCredentials() {
        String username = configuredOrFallback(credentialsProvider == null ? null : credentialsProvider.getUsername(), properties.getUsername());
        String password = configuredOrFallback(credentialsProvider == null ? null : credentialsProvider.getPassword(), properties.getPassword());
        if (username == null || password == null) {
            throw new IllegalStateException("APIHub OCR username or password is not configured.");
        }
        return new LoginCredentials(username, password);
    }

    private static String configuredOrFallback(String configured, String fallback) {
        String value = configured == null || configured.isBlank() ? fallback : configured;
        return value == null || value.isBlank() ? null : value;
    }

    private CachedToken requestLogin(LoginCredentials credentials) throws IOException, InterruptedException {
        String body = objectMapper.writeValueAsString(Map.of(
                properties.getLoginUsernameField(), credentials.username(), "password", credentials.password()));
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.getTokenUrl()))
                .timeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() != 200) {
            throw new IOException("APIHub OCR login HTTP error: " + response.statusCode());
        }
        return parseLoginResponse(response.body(), credentials);
    }

    private CachedToken parseLoginResponse(String body, LoginCredentials credentials) throws IOException {
        JsonNode data = readEnvelope(body, "login").path("data");
        String token = data.path("token").asText("");
        if (token.isBlank()) throw new IOException("APIHub OCR login returned no token");
        long remaining = data.path("tokenRemainTime").asLong(0);
        long cacheSeconds = Math.max(0, remaining - Math.min(60, remaining / 10));
        return new CachedToken(token, credentials.username(), credentials.password(), Instant.now().plusSeconds(cacheSeconds));
    }

    private JsonNode readEnvelope(String body, String operation) throws IOException {
        JsonNode response = objectMapper.readTree(body);
        if (response == null || !response.path("success").asBoolean(false)
                || !"000000".equals(response.path("code").asText())) {
            if ("search".equals(operation)) tokenCache.set(null);
            throw new IOException("APIHub OCR " + operation + " failed (code="
                    + (response == null ? "missing" : response.path("code").asText("missing")) + ")");
        }
        return response;
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
