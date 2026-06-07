package com.xiyu.bid.service;

import com.xiyu.bid.entity.PasswordResetToken;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordResetServiceCreateTokenTest extends AbstractPasswordResetServiceTest {

    @Test
    void createPasswordResetToken_WithValidEmail_ShouldCreateTokenAndSendEmail() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenReturn(testToken);
        when(emailService.sendPasswordResetEmail(anyString(), anyString())).thenReturn("raw-token-for-dev");

        String result = passwordResetService.createPasswordResetToken("test@example.com");

        assertThat(result).isEqualTo("raw-token-for-dev");
        verify(tokenRepository).save(any(PasswordResetToken.class));
        verify(emailService).sendPasswordResetEmail(eq("test@example.com"), anyString());
    }

    @Test
    void createPasswordResetToken_WithNonExistentEmail_ShouldThrowException() {
        when(userRepository.findByEmail("nonexistent@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService.createPasswordResetToken("nonexistent@example.com"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void createPasswordResetToken_WithDisabledUser_ShouldThrowException() {
        User disabledUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .fullName("Test User")
                .role(User.Role.STAFF)
                .enabled(false)
                .build();
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(disabledUser));

        assertThatThrownBy(() -> passwordResetService.createPasswordResetToken("test@example.com"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("disabled");

        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void createPasswordResetToken_ShouldSetExpirationToOneHour() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(PasswordResetToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.sendPasswordResetEmail(anyString(), anyString())).thenReturn("raw-token");

        passwordResetService.createPasswordResetToken("test@example.com");

        verify(tokenRepository).save(argThat(token ->
                token.getExpiresAt().isAfter(LocalDateTime.now())
                        && token.getExpiresAt().isBefore(LocalDateTime.now().plusHours(1).plusMinutes(1))
        ));
    }
}
