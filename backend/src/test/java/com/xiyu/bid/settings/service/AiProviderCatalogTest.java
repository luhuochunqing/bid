package com.xiyu.bid.settings.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiProviderCatalogTest {

    private final AiProviderCatalog catalog = new AiProviderCatalog();

    @Test
    void environmentKeys_ShouldExposeProviderSpecificFallbackOrder() {
        assertThat(catalog.environmentKeys("qwen")).containsExactly("DASHSCOPE_API_KEY", "QWEN_API_KEY");
        assertThat(catalog.environmentKeys("doubao"))
                .containsExactly("ARK_API_KEY", "DOUBAO_API_KEY", "VOLCENGINE_API_KEY");
    }

    @Test
    void validateBaseUrl_ShouldAllowOfficialHttpsHost() {
        catalog.validateBaseUrl("qwen", "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions");
    }

    @Test
    void validateBaseUrl_ShouldRejectUntrustedHost() {
        assertThatThrownBy(() -> catalog.validateBaseUrl("openai", "https://metadata.internal/latest"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI API 地址必须匹配当前厂商的官方域名");
    }

    @Test
    void validateBaseUrl_ShouldRejectNonHttpsUrl() {
        assertThatThrownBy(() -> catalog.validateBaseUrl("deepseek", "http://api.deepseek.com/chat/completions"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI API 地址必须是 HTTPS 完整地址");
    }
}
