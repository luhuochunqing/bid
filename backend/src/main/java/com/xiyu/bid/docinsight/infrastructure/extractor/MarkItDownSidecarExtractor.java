package com.xiyu.bid.docinsight.infrastructure.extractor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.docinsight.application.DocumentTextExtractor;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

@Component
@Slf4j
public class MarkItDownSidecarExtractor implements DocumentTextExtractor {

    private static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";

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
    public ExtractedDocument extract(String fileName, String contentType, byte[] content) {
        if (isPlainText(contentType)) {
            String text = new String(content, StandardCharsets.UTF_8);
            return new ExtractedDocument(
                    text,
                    text.length(),
                    null,
                    "plain-text",
                    Map.of()
            );
        }

        log.info("Sending document {} to MarkItDown sidecar...", fileName);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (!sidecarSharedKey.isBlank()) {
            headers.set(SIDECAR_KEY_HEADER, sidecarSharedKey);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(content) {
            @Override
            public String getFilename() {
                return fileName;
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        try {
            String responseStr = restTemplate.postForObject(sidecarUrl + "/convert", requestEntity, String.class);
            JsonNode root = objectMapper.readTree(responseStr);
            String markdown = root.path("markdown").asText("");
            
            if (markdown.isBlank()) {
                throw new IllegalStateException("Sidecar returned empty markdown");
            }

            return new ExtractedDocument(
                    markdown,
                    markdown.length(),
                    responseStr,
                    "markitdown-sidecar",
                    Map.of()
            );
        } catch (org.springframework.web.client.RestClientException | com.fasterxml.jackson.core.JsonProcessingException e) {
            log.error("Failed to extract text using Sidecar: {}", e.getMessage());
            throw new IllegalStateException("Failed to parse sidecar response or network error", e);
        }
    }

    private boolean isPlainText(String contentType) {
        if (contentType == null) {
            return false;
        }
        return "text/plain".equals(contentType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT));
    }
}
