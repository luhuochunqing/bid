package com.xiyu.bid.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilJtiTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-generation-and-validation";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L);

    @Test
    void generateAccessToken_shouldEmbedJtiClaim() {
        String token = jwtUtil.generateAccessToken("alice");

        String jti = jwtUtil.extractJti(token);
        assertThat(jti).isNotBlank();
        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.getId()).isEqualTo(jti);
    }

    @Test
    void generateAccessToken_shouldProduceUniqueJtiPerToken() {
        String first = jwtUtil.generateAccessToken("alice");
        String second = jwtUtil.generateAccessToken("alice");

        assertThat(jwtUtil.extractJti(first)).isNotEqualTo(jwtUtil.extractJti(second));
    }

    @Test
    void extractExpirationInstant_shouldReturnTokenExpiry() {
        String token = jwtUtil.generateAccessToken("alice");

        Instant exp = jwtUtil.extractExpirationInstant(token);
        assertThat(exp).isNotNull();
        assertThat(exp).isAfter(Instant.now());
    }

    @Test
    void extractJti_shouldReturnNullForGarbageInput() {
        assertThat(jwtUtil.extractJti("not-a-jwt")).isNull();
    }
}
