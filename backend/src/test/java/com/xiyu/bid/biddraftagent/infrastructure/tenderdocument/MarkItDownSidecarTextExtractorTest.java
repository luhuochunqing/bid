package com.xiyu.bid.biddraftagent.infrastructure.tenderdocument;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.application.ExtractedTenderDocument;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MarkItDownSidecarTextExtractorTest {

    @Mock
    private RestTemplate restTemplate;

    private MarkItDownSidecarTextExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new MarkItDownSidecarTextExtractor(
                restTemplate,
                new ObjectMapper(),
                "http://localhost:8000",
                ""
        );
    }

    @Test
    void shouldExtractTextViaSidecar() {
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

        byte[] content = "dummy-pdf-content".getBytes();
        ExtractedTenderDocument result = extractor.extract("test.pdf", "application/pdf", content);

        assertThat(result.text()).isEqualTo("# Header\nContent");
        assertThat(result.structuredMetadata()).isEqualTo(sidecarResponse);
        assertThat(result.extractorKey()).isEqualTo("markitdown-sidecar");
    }

    @Test
    void shouldSendSidecarKeyWhenConfigured() {
        extractor = new MarkItDownSidecarTextExtractor(
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

        extractor.extract("test.pdf", "application/pdf", "dummy-pdf-content".getBytes());

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), requestCaptor.capture(), eq(String.class));
        assertThat(requestCaptor.getValue().getHeaders().getFirst("X-Sidecar-Key"))
                .isEqualTo("test-sidecar-key");
    }

    @Test
    void shouldThrowExceptionWhenSidecarFails() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new RestClientException("Connection refused"));

        byte[] content = "dummy-pdf-content".getBytes();
        assertThatThrownBy(() -> extractor.extract("test.pdf", "application/pdf", content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("招标文件正文提取失败: sidecar HTTP failure: Connection refused");
    }

    @Test
    void shouldThrowExceptionWhenSidecarReturnsEmptyMarkdown() {
        String sidecarResponse = "{\"documentId\":\"test.pdf\",\"markdown\":\"\",\"sections\":[]}";
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(sidecarResponse);

        byte[] content = "dummy".getBytes();
        assertThatThrownBy(() -> extractor.extract("empty.pdf", "application/pdf", content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("招标文件正文提取失败: sidecar returned empty markdown");
    }

    @Test
    void shouldThrowExceptionWhenSidecarReturnsMalformedJson() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn("{ not-valid-json");

        byte[] content = "dummy".getBytes();
        assertThatThrownBy(() -> extractor.extract("broken.pdf", "application/pdf", content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("招标文件正文提取失败: sidecar response not parseable");
    }

    @Test
    void shouldThrowExceptionWhenSidecarReturnsNullBody() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenReturn(null);

        byte[] content = "dummy".getBytes();
        assertThatThrownBy(() -> extractor.extract("null.pdf", "application/pdf", content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("招标文件正文提取失败: sidecar returned empty body");
    }

    @Test
    void shouldThrowExceptionOnReadTimeoutFromDedicatedRestTemplate() {
        when(restTemplate.postForObject(anyString(), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Read timed out"));

        byte[] content = "dummy".getBytes();
        assertThatThrownBy(() -> extractor.extract("slow.pdf", "application/pdf", content))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("招标文件正文提取失败: sidecar HTTP failure: Read timed out");
    }
}
