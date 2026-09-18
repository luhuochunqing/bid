package com.xiyu.bid.docinsight.infrastructure.extractor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.docinsight.application.ExtractedDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 验证文档抽取遇到网络读取超时（ResourceAccessException）时，
 * 绝不允许触发 3 次重试（避免放大耗时到 184 秒并阻塞 Servlet 容器线程），
 * 必须立刻中断重试并降级到本地提取器。
 */
@SpringJUnitConfig(MarkItDownSidecarExtractorTimeoutNoRetryTest.TestConfig.class)
class MarkItDownSidecarExtractorTimeoutNoRetryTest {

    @Configuration
    @EnableRetry(proxyTargetClass = true)
    static class TestConfig {
        @Bean
        public RestTemplate markItDownSidecarRestTemplate() {
            return mock(RestTemplate.class);
        }

        @Bean
        public ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        public MarkItDownSidecarExtractor markItDownSidecarExtractor(RestTemplate markItDownSidecarRestTemplate, ObjectMapper objectMapper) {
            return new MarkItDownSidecarExtractor(
                    markItDownSidecarRestTemplate,
                    objectMapper,
                    "http://localhost:8000",
                    "test-key",
                    30L
            );
        }
    }

    @Autowired
    private MarkItDownSidecarExtractor extractor;

    @Autowired
    private RestTemplate restTemplate;

    @Test
    @DisplayName("读取超时(ResourceAccessException)必须禁止重试，仅调用1次即走本地fallback")
    void resourceAccessException_shouldNotRetry_andFallbackImmediately() {
        when(restTemplate.getForObject(anyString(), eq(String.class))).thenReturn("{\"status\":\"up\"}");
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new ResourceAccessException("Read timed out"));

        ExtractedDocument result = extractor.extract("sample.dat", "application/octet-stream", "Hello World fallback test".getBytes());

        // 验证：仅调用 1 次，不得重试
        verify(restTemplate, times(1)).postForObject(anyString(), any(), eq(String.class));
        assertThat(result).isNotNull();
        assertThat(result.text()).isEqualTo("Hello World fallback test");
        assertThat(result.extractorKey()).isEqualTo("sidecar-fallback-plaintext");
    }
}
