package com.xiyu.bid.docinsight.infrastructure.extractor;

import com.xiyu.bid.config.TraceHeaderInjector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class MarkItDownSidecarExtractor implements DocumentTextExtractor {

    private static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";
    private static final int MIN_USABLE_TEXT_LENGTH = 100;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String sidecarUrl;
    private final String sidecarSharedKey;

    public MarkItDownSidecarExtractor(
            @Qualifier("markItDownSidecarRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.doc-insight.sidecar-url:http://localhost:8000}") String sidecarUrl,
            @Value("${app.doc-insight.sidecar-shared-key:${APP_DOC_INSIGHT_SIDECAR_SHARED_KEY:${SIDECAR_SHARED_KEY:}}}") String sidecarSharedKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.sidecarUrl = sidecarUrl;
        this.sidecarSharedKey = sidecarSharedKey == null ? "" : sidecarSharedKey.trim();
    }

    @Override
    @Retryable(
            retryFor = {RestClientException.class, IllegalStateException.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0))
    public ExtractedDocument extract(String fileName, String contentType, byte[] content) {
        if (isPlainText(contentType)) {
            String text = new String(content, StandardCharsets.UTF_8);
            log.info("Plain text document extracted: fileName={}, length={}", fileName, text.length());
            return new ExtractedDocument(
                    text,
                    text.length(),
                    null,
                    "plain-text",
                    Map.of()
            );
        }

        log.info("Document extraction started: fileName={}, contentType={}, size={} bytes, sidecarUrl={}",
                fileName, contentType, content.length, sidecarUrl);

        // Sidecar health check before upload
        boolean sidecarHealthy = checkSidecarHealth();
        if (!sidecarHealthy) {
            log.warn("Sidecar health check failed for {}, proceeding with fallback extraction", sidecarUrl);
            return fallbackExtract(fileName, contentType, content, "sidecar_unhealthy");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!sidecarSharedKey.isBlank()) {
            headers.set(SIDECAR_KEY_HEADER, sidecarSharedKey);
            log.debug("Using sidecar shared key authentication");
        } else {
            log.warn("Sidecar shared key is not configured — sending request without authentication");
        }
        TraceHeaderInjector.inject(headers);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);

        long startTime = System.currentTimeMillis();
        try {
            String responseStr = restTemplate.postForObject(sidecarUrl + "/convert", requestEntity, String.class);
            long duration = System.currentTimeMillis() - startTime;

            JsonNode root = objectMapper.readTree(responseStr);
            String markdown = root.path("markdown").asText("");
            JsonNode warningsNode = root.path("warnings");
            String converter = root.path("converter").asText("unknown");

            log.info("Sidecar extraction completed: fileName={}, duration={}ms, converter={}, markdownLength={}, warnings={}",
                    fileName, duration, converter, markdown.length(), warningsNode);

            if (markdown.isBlank()) {
                log.warn("Sidecar returned empty markdown for {}, falling back to local extraction", fileName);
                return fallbackExtract(fileName, contentType, content, "sidecar_empty_markdown");
            }

            if (markdown.trim().length() < MIN_USABLE_TEXT_LENGTH) {
                log.warn("Sidecar returned very short text ({} chars) for {}, may be a scanned document",
                        markdown.trim().length(), fileName);
            }

            return new ExtractedDocument(
                    markdown,
                    markdown.length(),
                    responseStr,
                    "markitdown-sidecar",
                    Map.of("converter", converter, "sidecarUrl", sidecarUrl)
            );
        } catch (JsonProcessingException e) {
            log.error("Failed to parse sidecar response for {}: {}", fileName, e.getMessage());
            return fallbackExtract(fileName, contentType, content, "sidecar_json_parse_error");
        }
    }

    /**
     * Check sidecar health before attempting document conversion.
     */
    private boolean checkSidecarHealth() {
        try {
            restTemplate.getForObject(sidecarUrl + "/health", String.class);
            return true;
        } catch (ResourceAccessException e) {
            log.warn("Sidecar connection refused at {}: {}", sidecarUrl, e.getMessage());
            return false;
        } catch (RestClientException e) {
            log.warn("Sidecar health check failed at {}: {}", sidecarUrl, e.getMessage());
            return false;
        }
    }

    @Recover
    public ExtractedDocument recover(RestClientException e, String fileName, String contentType, byte[] content) {
        log.warn("Sidecar extraction failed after retries for {} ({}): {}, falling back to local extraction",
                fileName, contentType, e.getMessage());
        return fallbackExtract(fileName, contentType, content, "sidecar_rest_exception");
    }

    @Recover
    public ExtractedDocument recover(IllegalStateException e, String fileName, String contentType, byte[] content) {
        log.warn("Sidecar returned invalid result after retries for {} ({}): {}, falling back to local extraction",
                fileName, contentType, e.getMessage());
        return fallbackExtract(fileName, contentType, content, "sidecar_invalid_result");
    }

    /**
     * Fallback extraction when sidecar fails.
     * For .doc files: uses Apache POI HWPF.
     * For .pdf files: uses Apache PDFBox.
     * For other files: attempts plain text fallback (best effort, may produce garbage for binary files).
     */
    private ExtractedDocument fallbackExtract(String fileName, String contentType, byte[] content, String reason) {
        if (content == null || content.length == 0) {
            throw new IllegalStateException("Sidecar extraction failed for " + fileName
                    + " and no fallback content available");
        }

        String lowerName = fileName.toLowerCase(Locale.ROOT);

        // Try Apache POI HWPF for .doc files
        if (lowerName.endsWith(".doc") || "application/msword".equals(contentType)) {
            String text = extractDocWithPoi(content);
            if (text != null && !text.isBlank()) {
                log.info("POI HWPF fallback extraction succeeded for {}: {} chars", fileName, text.length());
                return new ExtractedDocument(
                        text,
                        text.length(),
                        null,
                        "poi-hwpf-fallback",
                        Map.of("fallbackReason", reason, "originalExtractor", "markitdown-sidecar")
                );
            }
            log.warn("POI HWPF fallback extraction returned empty for {}", fileName);
        }

        // Try Apache PDFBox for .pdf files
        if (lowerName.endsWith(".pdf") || "application/pdf".equals(contentType)) {
            String text = extractPdfWithPdfBox(content);
            if (text != null && !text.isBlank()) {
                log.info("PDFBox fallback extraction succeeded for {}: {} chars", fileName, text.length());
                return new ExtractedDocument(
                        text,
                        text.length(),
                        null,
                        "pdfbox-fallback",
                        Map.of("fallbackReason", reason, "originalExtractor", "markitdown-sidecar")
                );
            }
            log.warn("PDFBox fallback extraction returned empty for {}", fileName);
        }

        // Last resort: plain text (will likely be garbage for binary files)
        String text = new String(content, StandardCharsets.UTF_8);
        int printableCount = 0;
        for (char c : text.toCharArray()) {
            if (c >= 32 && c <= 126) printableCount++;
        }
        log.warn("Fallback plain text extraction for {}: totalChars={}, printableChars={}, ratio={:.1f}%, reason={}",
                fileName, text.length(), printableCount,
                text.length() > 0 ? (100.0 * printableCount / text.length()) : 0,
                reason);

        return new ExtractedDocument(
                text,
                text.length(),
                null,
                "sidecar-fallback-plaintext",
                Map.of("fallbackReason", reason, "printableChars", String.valueOf(printableCount))
        );
    }

    /**
     * Extract text from .doc file using Apache POI HWPF.
     * Returns null if extraction fails.
     */
    private String extractDocWithPoi(byte[] content) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             HWPFDocument doc = new HWPFDocument(bis);
             WordExtractor extractor = new WordExtractor(doc)) {
            String text = extractor.getText();
            if (text != null) {
                return text.trim();
            }
        } catch (IOException e) {
            log.warn("POI HWPF extraction failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("POI HWPF extraction unexpected error: {}", e.getMessage());
        }
        return null;
    }

    /**
     * Extract text from .pdf file using Apache PDFBox.
     * Returns null if extraction fails.
     */
    private String extractPdfWithPdfBox(byte[] content) {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(content);
             PDDocument document = PDDocument.load(bis)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(document);
            if (text != null) {
                return text.trim()
                        .replace('\u00A0', ' ')
                        .replaceAll(" {2,}", " ");
            }
        } catch (IOException e) {
            log.warn("PDFBox extraction failed: {}", e.getMessage());
        } catch (RuntimeException e) {
            log.warn("PDFBox extraction unexpected error: {}", e.getMessage());
        }
        return null;
    }

    private boolean isPlainText(String contentType) {
        if (contentType == null) {
            return false;
        }
        return "text/plain".equals(contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT));
    }
}
