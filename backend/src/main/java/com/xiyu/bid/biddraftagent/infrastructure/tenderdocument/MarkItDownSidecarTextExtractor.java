// Input: 原始文件字节、文件名和可选共享密钥；通过 HTTP 调用 MarkItDown sidecar 做 doc→markdown 转换
// Output: ExtractedTenderDocument（含 markdown 正文 + 结构化元数据）；带 X-Sidecar-Key 鉴权

package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.xiyu.bid.config.TraceHeaderInjector;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import com.xiyu.bid.biddraftagent.application.TenderDocumentTextExtractor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * @deprecated 已由 {@link com.xiyu.bid.docinsight.infrastructure.extractor.MarkItDownSidecarExtractor} 取代。
 *             新代码请使用 docinsight 模块的提取器。将在 next-release 移除。
 */
@Deprecated(since = "next-release", forRemoval = true)
@Component
@Primary
@Profile("!e2e")
@Slf4j
public class MarkItDownSidecarTextExtractor implements TenderDocumentTextExtractor {

    private static final String SIDECAR_KEY_HEADER = "X-Sidecar-Key";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String sidecarUrl;
    private final String sidecarSharedKey;

    public MarkItDownSidecarTextExtractor(
            @Qualifier("markItDownSidecarRestTemplate") RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${app.converter.sidecar-url:http://localhost:8000}") String sidecarUrl,
            @Value("${app.converter.sidecar-shared-key:${APP_CONVERTER_SIDECAR_SHARED_KEY:${SIDECAR_SHARED_KEY:}}}") String sidecarSharedKey) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.sidecarUrl = sidecarUrl;
        this.sidecarSharedKey = sidecarSharedKey == null ? "" : sidecarSharedKey.trim();
    }

    @Override
    public ExtractedTenderDocument extract(String fileName, String contentType, byte[] content) {
        try {
            log.info("Sending document {} to MarkItDown sidecar...", fileName);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            if (!sidecarSharedKey.isBlank()) {
                headers.set(SIDECAR_KEY_HEADER, sidecarSharedKey);
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
            String responseStr = restTemplate.postForObject(sidecarUrl + "/convert", requestEntity, String.class);
            if (responseStr == null || responseStr.isBlank()) {
                return fallbackOnFailure(fileName, contentType, content, "sidecar returned empty body");
            }

            JsonNode root = objectMapper.readTree(responseStr);
            String markdown = root.path("markdown").asText("");
            if (markdown.isBlank()) {
                return fallbackOnFailure(fileName, contentType, content, "sidecar returned empty markdown");
            }

            return new ExtractedTenderDocument(
                    fileName,
                    contentType,
                    markdown,
                    markdown.length(),
                    "markitdown-sidecar",
                    responseStr
            );
        } catch (RestClientException httpFailure) {
            return fallbackOnFailure(fileName, contentType, content, "sidecar HTTP failure: " + httpFailure.getMessage());
        } catch (JsonProcessingException parseFailure) {
            return fallbackOnFailure(fileName, contentType, content, "sidecar response not parseable: " + parseFailure.getMessage());
        }
    }

    private ExtractedTenderDocument fallbackOnFailure(String fileName, String contentType, byte[] content, String reason) {
        log.error("Tender document extraction failed for {}: {}", fileName, reason);
        throw new IllegalStateException("招标文件正文提取失败: " + reason);
    }
}
