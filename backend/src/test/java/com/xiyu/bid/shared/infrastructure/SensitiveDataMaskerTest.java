package com.xiyu.bid.shared.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {

    @Test
    void shouldMaskTokenWithPrefixAndSuffix() {
        String masked = SensitiveDataMasker.maskToken("abcdef1234567890");
        assertThat(masked).isEqualTo("abcd****7890");
    }

    @Test
    void shouldMaskShortToken() {
        String masked = SensitiveDataMasker.maskToken("abc12");
        assertThat(masked).isEqualTo("****");
    }

    @Test
    void shouldMaskNullToken() {
        assertThat(SensitiveDataMasker.maskToken(null)).isEqualTo("****");
    }

    @Test
    void shouldMaskMobileNumber() {
        String masked = SensitiveDataMasker.maskMobile("13812345678");
        assertThat(masked).isEqualTo("138****5678");
    }

    @Test
    void shouldMaskShortMobile() {
        assertThat(SensitiveDataMasker.maskMobile("12345")).isEqualTo("****");
    }

    @Test
    void shouldMaskEmailPreservingDomain() {
        String masked = SensitiveDataMasker.maskEmail("zhangsan@xiyu.com");
        assertThat(masked).isEqualTo("z***@xiyu.com");
    }

    @Test
    void shouldMaskShortEmailLocal() {
        String masked = SensitiveDataMasker.maskEmail("a@xiyu.com");
        assertThat(masked).isEqualTo("*@xiyu.com");
    }

    @Test
    void shouldHandleNullEmail() {
        assertThat(SensitiveDataMasker.maskEmail(null)).isEqualTo("****");
    }

    @Test
    void shouldHandleEmailWithoutAt() {
        assertThat(SensitiveDataMasker.maskEmail("no-at-sign")).isEqualTo("****");
    }
}
