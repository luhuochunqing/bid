package com.xiyu.bid.integration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeComCredentialValidation — pure validation rules")
class WeComCredentialValidationTest {

    @Test
    @DisplayName("valid credential passes validation")
    void validate_valid() {
        WeComCredential credential = new WeComCredential(
                "ww1234567890abcdef",
                "1000001",
                "my-corp-secret",
                false,
                true
        );
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    @DisplayName("blank corpId produces error")
    void validate_corpId_blank() {
        WeComCredential credential = new WeComCredential("", "1000001", "secret", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("corpId"));
    }

    @Test
    @DisplayName("null corpId produces error")
    void validate_corpId_null() {
        WeComCredential credential = new WeComCredential(null, "1000001", "secret", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("corpId"));
    }

    @Test
    @DisplayName("corpId exceeding 64 chars produces error")
    void validate_corpId_tooLong() {
        String long65 = "a".repeat(65);
        WeComCredential credential = new WeComCredential(long65, "1000001", "secret", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("corpId"));
    }

    @Test
    @DisplayName("agentId with non-numeric chars produces error")
    void validate_agentId_nonNumeric() {
        WeComCredential credential = new WeComCredential("wwcorp", "abc123", "secret", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("agentId"));
    }

    @Test
    @DisplayName("blank agentId produces error")
    void validate_agentId_blank() {
        WeComCredential credential = new WeComCredential("wwcorp", "", "secret", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("agentId"));
    }

    @Test
    @DisplayName("agentId exceeding 32 chars produces error")
    void validate_agentId_tooLong() {
        String long33 = "1".repeat(33);
        WeComCredential credential = new WeComCredential("wwcorp", long33, "secret", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("agentId"));
    }

    @Test
    @DisplayName("blank corpSecret produces error")
    void validate_corpSecret_blank() {
        WeComCredential credential = new WeComCredential("wwcorp", "1000001", "", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).anyMatch(e -> e.contains("corpSecret"));
    }

    @Test
    @DisplayName("multiple violations produce multiple errors")
    void validate_multiple_errors() {
        WeComCredential credential = new WeComCredential("", "abc", "", false, false);
        ValidationResult result = WeComCredentialValidation.validate(credential);
        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).hasSizeGreaterThanOrEqualTo(2);
    }
}
