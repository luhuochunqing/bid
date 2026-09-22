package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiProviderDiagnosticsTest {

    @Test
    void shouldSanitizeApiKeyInErrorMessage() {
        String rawError = "Request to https://api.deepseek.com failed with Bearer sk-ant-api03-abcdef1234567890 and key sk-1234567890abcdef";
        String sanitized = AiProviderDiagnostics.sanitize(rawError);

        assertThat(sanitized).doesNotContain("sk-ant-api03-abcdef1234567890");
        assertThat(sanitized).doesNotContain("sk-1234567890abcdef");
        assertThat(sanitized).contains("sk-***");
    }

    @Test
    void shouldCreateSuccessDiagnostics() {
        AiProviderDiagnostics diagnostics = AiProviderDiagnostics.success(
                "deepseek", "deepseek-chat", 1250L,
                List.of(new AiProviderDiagnostics.AiCallDetail("TABLE_EXTRACTION", "deepseek", "deepseek-chat", "SUCCESS", 1250L, null, null))
        );

        assertThat(diagnostics.provider()).isEqualTo("deepseek");
        assertThat(diagnostics.model()).isEqualTo("deepseek-chat");
        assertThat(diagnostics.status()).isEqualTo("SUCCESS");
        assertThat(diagnostics.latencyMs()).isEqualTo(1250L);
        assertThat(diagnostics.errorClass()).isNull();
        assertThat(diagnostics.errorMessage()).isNull();
        assertThat(diagnostics.calls()).hasSize(1);

        Map<String, Object> map = diagnostics.toMap();
        assertThat(map)
                .containsEntry("provider", "deepseek")
                .containsEntry("model", "deepseek-chat")
                .containsEntry("status", "SUCCESS")
                .containsEntry("latencyMs", 1250L);
        assertThat(map).doesNotContainKey("errorClass");
        assertThat(map).doesNotContainKey("errorMessage");
        assertThat(map).containsKey("calls");
    }

    @Test
    void shouldCreateFailureDiagnosticsWithSanitizedMessage() {
        RuntimeException ex = new IllegalStateException("Failed with authorization: Bearer sk-secretkey123456789");
        AiProviderDiagnostics diagnostics = AiProviderDiagnostics.failure(
                "custom", "custom-model", 500L, ex
        );

        assertThat(diagnostics.provider()).isEqualTo("custom");
        assertThat(diagnostics.model()).isEqualTo("custom-model");
        assertThat(diagnostics.status()).isEqualTo("FAILED");
        assertThat(diagnostics.latencyMs()).isEqualTo(500L);
        assertThat(diagnostics.errorClass()).isEqualTo("IllegalStateException");
        assertThat(diagnostics.errorMessage()).doesNotContain("sk-secretkey123456789");
        assertThat(diagnostics.errorMessage()).contains("sk-***");

        Map<String, Object> map = diagnostics.toMap();
        assertThat(map)
                .containsEntry("provider", "custom")
                .containsEntry("model", "custom-model")
                .containsEntry("status", "FAILED")
                .containsEntry("errorClass", "IllegalStateException");
    }

    @Test
    void shouldHandleEmptyAndNullSafely() {
        AiProviderDiagnostics empty = AiProviderDiagnostics.empty();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.toMap()).isEmpty();

        String sanitizedNull = AiProviderDiagnostics.sanitize(null);
        assertThat(sanitizedNull).isNull();

        String sanitizedBlank = AiProviderDiagnostics.sanitize("   ");
        assertThat(sanitizedBlank).isEqualTo("   ");
    }
}
