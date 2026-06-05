package com.xiyu.bid.integration.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("WeComCredential — value object contract")
class WeComCredentialTest {

    @Test
    @DisplayName("two credentials with same fields are equal")
    void equals_samFields() {
        WeComCredential a = new WeComCredential("wwcorp", "1000001", "secret", true, false);
        WeComCredential b = new WeComCredential("wwcorp", "1000001", "secret", true, false);
        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    @DisplayName("different corpId makes credentials not equal")
    void equals_differentCorpId() {
        WeComCredential a = new WeComCredential("wwcorp1", "1000001", "secret", false, false);
        WeComCredential b = new WeComCredential("wwcorp2", "1000001", "secret", false, false);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("toString() masks the corpSecret value")
    void toString_maskSecret() {
        WeComCredential c = new WeComCredential("wwcorp", "1000001", "super-secret-value", false, true);
        String str = c.toString();
        assertThat(str).doesNotContain("super-secret-value");
        assertThat(str).contains("corpSecret=***");
        assertThat(str).contains("wwcorp");
        assertThat(str).contains("1000001");
    }

    @Test
    @DisplayName("fields are accessible via record accessors")
    void accessors() {
        WeComCredential c = new WeComCredential("wwcorp", "1000001", "mysecret", true, true);
        assertThat(c.corpId()).isEqualTo("wwcorp");
        assertThat(c.agentId()).isEqualTo("1000001");
        assertThat(c.corpSecret()).isEqualTo("mysecret");
        assertThat(c.ssoEnabled()).isTrue();
        assertThat(c.messageEnabled()).isTrue();
    }
}
