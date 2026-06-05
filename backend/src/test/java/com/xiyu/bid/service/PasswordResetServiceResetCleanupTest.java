package com.xiyu.bid.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceResetCleanupTest extends AbstractPasswordResetServiceTest {

    @Test
    void resetPassword_WithValidToken_ShouldUpdatePasswordAndMarkTokenUsed() {
        String rawToken = "valid-reset-token";
        String hashedToken = hashTokenForTest(rawToken);
        String newPassword = "NewSecurePassword123!";
        testToken.setToken(hashedToken);
        testToken.setUsedAt(null);

        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(testToken));
        when(passwordEncoder.encode(newPassword)).thenReturn("newEncodedPassword");
        when(userRepository.save(any())).thenReturn(testUser);

        passwordResetService.resetPassword(rawToken, newPassword);

        verify(passwordEncoder).encode(newPassword);
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(user ->
                "newEncodedPassword".equals(user.getPassword())
        ));
        verify(tokenRepository).markAsUsed(eq(hashedToken), any(LocalDateTime.class));
    }

    @Test
    void resetPassword_WithInvalidToken_ShouldThrowException() {
        String rawToken = "invalid-token";
        String hashedToken = hashTokenForTest(rawToken);
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "NewPassword123!"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid or expired");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_WithUsedToken_ShouldThrowException() {
        String rawToken = "already-used-token";
        String hashedToken = hashTokenForTest(rawToken);
        testToken.setToken(hashedToken);
        testToken.setUsedAt(LocalDateTime.now());
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(testToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "NewPassword123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already been used");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void resetPassword_WithExpiredToken_ShouldThrowException() {
        String rawToken = "expired-token";
        String hashedToken = hashTokenForTest(rawToken);
        testToken.setToken(hashedToken);
        testToken.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        when(tokenRepository.findByToken(hashedToken)).thenReturn(Optional.of(testToken));

        assertThatThrownBy(() -> passwordResetService.resetPassword(rawToken, "NewPassword123!"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("expired");

        verify(passwordEncoder, never()).encode(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void cleanupExpiredTokens_ShouldDeleteExpiredAndUsedTokens() {
        when(tokenRepository.deleteExpiredOrUsedTokens(any(LocalDateTime.class))).thenReturn(5);

        passwordResetService.cleanupExpiredTokens();

        verify(tokenRepository).deleteExpiredOrUsedTokens(any(LocalDateTime.class));
    }

    @Test
    void cleanupExpiredTokens_WithNoTokensToDelete_ShouldHandleGracefully() {
        when(tokenRepository.deleteExpiredOrUsedTokens(any(LocalDateTime.class))).thenReturn(0);

        passwordResetService.cleanupExpiredTokens();

        verify(tokenRepository).deleteExpiredOrUsedTokens(any(LocalDateTime.class));
    }
}
