package com.xiyu.bid.compliance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("百度 PaddleOCR-VL 客户端测试")
class BaiduPaddleVlClientTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("未配置 API Key 或 Secret Key 时 isConfigured 返回 false")
    void testIsConfigured_whenKeysMissing() {
        BaiduPaddleVlClient client1 = new BaiduPaddleVlClient(objectMapper, true, "", "secret", 30);
        assertThat(client1.isConfigured()).isFalse();

        BaiduPaddleVlClient client2 = new BaiduPaddleVlClient(objectMapper, true, "api_key", "  ", 30);
        assertThat(client2.isConfigured()).isFalse();

        BaiduPaddleVlClient client3 = new BaiduPaddleVlClient(objectMapper, false, "api_key", "secret", 30);
        assertThat(client3.isConfigured()).isFalse();
    }

    @Test
    @DisplayName("完整配置且 enabled 时 isConfigured 返回 true")
    void testIsConfigured_whenValid() {
        BaiduPaddleVlClient client = new BaiduPaddleVlClient(objectMapper, true, "api_key_123", "secret_123", 30);
        assertThat(client.isConfigured()).isTrue();
    }

    @Test
    @DisplayName("未启用或参数为空时 parseDocument 优雅降级返回 empty")
    void testParseDocument_disabledOrEmptyInput_returnsEmpty() {
        BaiduPaddleVlClient disabledClient = new BaiduPaddleVlClient(objectMapper, false, "api_key", "secret", 30);
        Optional<String> result1 = disabledClient.parseDocument(new byte[]{1, 2, 3}, "test.png");
        assertThat(result1).isEmpty();

        BaiduPaddleVlClient activeClient = new BaiduPaddleVlClient(objectMapper, true, "api_key", "secret", 30);
        Optional<String> result2 = activeClient.parseDocument(null, "test.png");
        assertThat(result2).isEmpty();

        Optional<String> result3 = activeClient.parseDocument(new byte[0], "test.png");
        assertThat(result3).isEmpty();

        Optional<String> result4 = activeClient.parseDocument(new byte[]{1, 2, 3}, null);
        assertThat(result4).isEmpty();
    }
}
