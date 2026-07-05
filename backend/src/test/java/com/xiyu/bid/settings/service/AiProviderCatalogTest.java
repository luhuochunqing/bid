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

    // ── custom Provider 测试 ──

    @Test
    void isSupported_ShouldAcceptCustom() {
        assertThat(catalog.isSupported("custom")).isTrue();
        assertThat(catalog.isSupported("CUSTOM")).isTrue();
        assertThat(catalog.isSupported("Custom")).isTrue();
    }

    @Test
    void supportedProviderCodes_ShouldIncludeCustomLast() {
        assertThat(catalog.supportedProviderCodes()).containsExactly("openai", "deepseek", "qwen", "doubao", "custom");
    }

    @Test
    void isCustomProvider_ShouldDetectCustom() {
        assertThat(AiProviderCatalog.isCustomProvider("custom")).isTrue();
        assertThat(AiProviderCatalog.isCustomProvider("CUSTOM")).isTrue();
        assertThat(AiProviderCatalog.isCustomProvider("openai")).isFalse();
        assertThat(AiProviderCatalog.isCustomProvider(null)).isFalse();
        assertThat(AiProviderCatalog.isCustomProvider("")).isFalse();
    }

    @Test
    void customProvider_EnvironmentKeysShouldBeEmpty() {
        assertThat(catalog.environmentKeys("custom")).isEmpty();
    }

    @Test
    void customProvider_DefaultSettingShouldHaveNullBaseUrl() {
        var setting = catalog.defaultProviderSetting("custom");
        assertThat(setting.getProviderCode()).isEqualTo("custom");
        assertThat(setting.getProviderName()).isEqualTo("自定义");
        assertThat(setting.getBaseUrl()).isNull();
        assertThat(setting.getModel()).isEmpty();
        assertThat(setting.getEnabled()).isTrue();
    }

    @Test
    void customProvider_ValidateBaseUrlShouldAllowHttp() {
        // custom Provider 允许 HTTP（如本地 Ollama）
        catalog.validateBaseUrl("custom", "http://localhost:11434/v1/chat/completions");
    }

    @Test
    void customProvider_ValidateBaseUrlShouldAllowAnyDomain() {
        // custom Provider 允许任意域名（如 OpenRouter、硅基流动）
        catalog.validateBaseUrl("custom", "https://openrouter.ai/api/v1/chat/completions");
    }

    @Test
    void customProvider_ValidateBaseUrlShouldRejectCloudMetadata() {
        // custom Provider 禁止云元数据地址
        assertThatThrownBy(() -> catalog.validateBaseUrl("custom", "http://169.254.169.254/latest/meta-data/"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不允许指向该地址");
    }

    @Test
    void customProvider_ValidateBaseUrlShouldRejectEmpty() {
        assertThatThrownBy(() -> catalog.validateBaseUrl("custom", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不能为空");
    }
}
