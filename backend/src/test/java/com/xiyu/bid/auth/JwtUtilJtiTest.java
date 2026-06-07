package com.xiyu.bid.auth;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JwtUtilJtiTest {

    private static final String SECRET = "test-secret-key-for-jwt-token-generation-and-validation";

    private final JwtUtil jwtUtil = new JwtUtil(SECRET, 3_600_000L);

    @Test
    void generateAccessToken_shouldEmbedJtiClaim() {
        String token = jwtUtil.generateAccessToken("alice");

        Optional<String> jtiOpt = jwtUtil.extractJti(token);
        assertThat(jtiOpt).isPresent();
        String jti = jtiOpt.get();
        assertThat(jti).isNotBlank();
        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.getId()).isEqualTo(jti);
    }

    @Test
    void generateAccessToken_shouldProduceUniqueJtiPerToken() {
        String first = jwtUtil.generateAccessToken("alice");
        String second = jwtUtil.generateAccessToken("alice");

        assertThat(jwtUtil.extractJti(first)).isNotEqualTo(jwtUtil.extractJti(second));
        assertThat(jwtUtil.extractJti(first).orElse("")).isNotEqualTo(jwtUtil.extractJti(second).orElse(""));
    }

    @Test
    void extractExpirationInstant_shouldReturnTokenExpiry() {
        String token = jwtUtil.generateAccessToken("alice");

        Optional<Instant> expOpt = jwtUtil.extractExpirationInstant(token);
        assertThat(expOpt).isPresent();
        Instant exp = expOpt.get();
        assertThat(exp).isAfter(Instant.now());
    }

    @Test
    void extractJti_shouldReturnEmptyForGarbageInput() {
        assertThat(jwtUtil.extractJti("not-a-jwt")).isEmpty();
    }

    @Test
    void extractExpirationInstant_shouldReturnEmptyForGarbageInput() {
        assertThat(jwtUtil.extractExpirationInstant("not-a-jwt")).isEmpty();
    }
}
