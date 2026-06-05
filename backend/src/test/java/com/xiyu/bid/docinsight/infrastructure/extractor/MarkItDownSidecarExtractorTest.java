package com.xiyu.bid.docinsight.infrastructure.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkItDownSidecarExtractorTest {

    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldSendSidecarKeyWhenConfigured() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key"
        );
        String sidecarResponse = """
                {
                  "documentId": "test.pdf",
                  "markdown": "# Header\\nContent",
                  "sections": [],
                  "warnings": [],
                  "converter": "markitdown",
                  "contentHash": "12345"
                }
                """;
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(sidecarResponse);

        ExtractedDocument result = extractor.extract("test.pdf", "application/pdf", "dummy".getBytes());

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        assertThat(result.extractorKey()).isEqualTo("markitdown-sidecar");
        assertThat(requestCaptor.getValue().getHeaders().getFirst("X-Sidecar-Key"))
                .isEqualTo("test-sidecar-key");
    }

    @Test
    void shouldExtractPlainTextWithoutSidecar() {
        MarkItDownSidecarExtractor extractor = new MarkItDownSidecarExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                "test-sidecar-key"
        );

        ExtractedDocument result = extractor.extract(
                "paste.txt",
                "text/plain; charset=UTF-8",
                "标题：南方电网供应链数字化项目".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(result.text()).isEqualTo("标题：南方电网供应链数字化项目");
        assertThat(result.textLength()).isEqualTo(result.text().length());
        assertThat(result.extractorKey()).isEqualTo("plain-text");
        verify(restTemplate, never()).postForObject(anyString(), any(HttpEntity.class), eq(String.class));
    }
}
