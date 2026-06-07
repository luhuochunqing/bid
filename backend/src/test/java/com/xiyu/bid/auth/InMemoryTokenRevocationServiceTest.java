package com.xiyu.bid.auth;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryTokenRevocationServiceTest {

    private final InMemoryTokenRevocationService service = new InMemoryTokenRevocationService();

    @Test
    void shouldReportRevokedAfterRevoke() {
        service.revoke("jti-1", Instant.now().plusSeconds(60));

        assertThat(service.isRevoked("jti-1")).isTrue();
    }

    @Test
    void shouldNotReportNonRevoked() {
        assertThat(service.isRevoked("jti-unknown")).isFalse();
    }

    @Test
    void shouldExpireAfterTtl() {
        service.revoke("jti-2", Instant.now().minusSeconds(1));

        assertThat(service.isRevoked("jti-2")).isFalse();
    }

    @Test
    void shouldHandleNullJti() {
        service.revoke(null, Instant.now().plusSeconds(60));
        assertThat(service.isRevoked(null)).isFalse();
        assertThat(service.isRevoked("")).isFalse();
    }
}
