package com.xiyu.bid.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class PasswordResetServiceValidateTokenTest extends AbstractPasswordResetServiceTest {

    @Test
    void validateToken_WithValidToken_ShouldReturnTrue() {
        String rawToken = "valid-raw-token";
        String hashedToken = hashTokenForTest(rawToken);
        testToken.setToken(hashedToken);
        testToken.setUsedAt(null);
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.validateToken(rawToken);

        assertThat(result).isTrue();
    }

    @Test
    void validateToken_WithUsedToken_ShouldReturnFalse() {
        String rawToken = "used-token";
        String hashedToken = hashTokenForTest(rawToken);
        testToken.setToken(hashedToken);
        testToken.setUsedAt(LocalDateTime.now());
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.validateToken(rawToken);

        assertThat(result).isFalse();
    }

    @Test
    void validateToken_WithExpiredToken_ShouldReturnFalse() {
        String rawToken = "expired-token";
        String hashedToken = hashTokenForTest(rawToken);
        testToken.setToken(hashedToken);
        testToken.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(testToken));

        boolean result = passwordResetService.validateToken(rawToken);

        assertThat(result).isFalse();
    }

    @Test
    void validateToken_WithNonExistentToken_ShouldReturnFalse() {
        String rawToken = "non-existent-token";
        String hashedToken = hashTokenForTest(rawToken);
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.empty());

        boolean result = passwordResetService.validateToken(rawToken);

        assertThat(result).isFalse();
    }
}
