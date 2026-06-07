package com.xiyu.bid.service;

import com.xiyu.bid.entity.EmailVerificationToken;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.EmailVerificationTokenRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for EmailVerificationService
 * Tests cover: token creation, email verification, token expiration, edge cases
 */
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock
    private EmailVerificationTokenRepository tokenRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    private User testUser;
    private EmailVerificationToken testToken;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .emailVerified(false)
                .build();

        testToken = EmailVerificationToken.builder()
                .id(1L)
                .token("hashed-token")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    void testCreateVerificationToken_Success() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenReturn(testToken);
        when(emailService.sendVerificationEmail(anyString(), anyString())).thenReturn("Email sent successfully");

        String result = emailVerificationService.createVerificationToken(1L);

        assertNotNull(result);
        assertEquals("Email sent successfully", result);
        verify(userRepository).findById(1L);
        verify(tokenRepository).save(any(EmailVerificationToken.class));
        verify(emailService).sendVerificationEmail(eq("test@example.com"), anyString());
    }

    @Test
    void testCreateVerificationToken_UserNotFound() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            emailVerificationService.createVerificationToken(999L);
        });

        verify(userRepository).findById(999L);
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void testCreateVerificationToken_EmailAlreadyVerified() {
        testUser.setEmailVerified(true);
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        assertThrows(IllegalStateException.class, () -> {
            emailVerificationService.createVerificationToken(1L);
        });

        verify(userRepository).findById(1L);
        verify(tokenRepository, never()).save(any());
        verify(emailService, never()).sendVerificationEmail(anyString(), anyString());
    }

    @Test
    void testVerifyEmail_Success() {
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(testToken));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenReturn(testToken);

        emailVerificationService.verifyEmail("raw-token");

        assertTrue(testUser.getEmailVerified());
        assertNotNull(testToken.getVerifiedAt());
        verify(tokenRepository).findByToken(anyString());
        verify(userRepository).save(testUser);
        verify(tokenRepository).save(testToken);
    }

    @Test
    void testVerifyEmail_InvalidToken() {
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> {
            emailVerificationService.verifyEmail("invalid-token");
        });

        verify(tokenRepository).findByToken(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testVerifyEmail_TokenAlreadyUsed() {
        testToken.setVerifiedAt(LocalDateTime.now());
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(testToken));

        assertThrows(IllegalStateException.class, () -> {
            emailVerificationService.verifyEmail("used-token");
        });

        verify(tokenRepository).findByToken(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testVerifyEmail_TokenExpired() {
        testToken.setExpiresAt(LocalDateTime.now().minusHours(1));
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(testToken));

        assertThrows(IllegalStateException.class, () -> {
            emailVerificationService.verifyEmail("expired-token");
        });

        verify(tokenRepository).findByToken(anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    void testTokenIsHashed() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> {
            EmailVerificationToken saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });
        when(emailService.sendVerificationEmail(anyString(), anyString())).thenReturn("Email sent");

        emailVerificationService.createVerificationToken(1L);

        verify(tokenRepository).save(argThat(token -> !token.getToken().equals("raw-uuid-token")));
    }

    @Test
    void testTokenExpirationTime() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(emailService.sendVerificationEmail(anyString(), anyString())).thenReturn("Email sent");

        emailVerificationService.createVerificationToken(1L);

        verify(tokenRepository).save(argThat(token -> {
            LocalDateTime now = LocalDateTime.now();
            return token.getExpiresAt().isAfter(now.plusHours(23)) &&
                   token.getExpiresAt().isBefore(now.plusHours(25));
        }));
    }

    @Test
    void testVerifyEmailUpdatesUserEmailVerifiedFlag() {
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(testToken));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenReturn(testToken);

        emailVerificationService.verifyEmail("valid-token");

        verify(userRepository).save(argThat(user -> Boolean.TRUE.equals(user.getEmailVerified())));
    }

    @Test
    void testVerifyEmailSetsVerifiedAtTimestamp() {
        when(tokenRepository.findByToken(anyString())).thenReturn(Optional.of(testToken));
        when(userRepository.save(any(User.class))).thenReturn(testUser);
        when(tokenRepository.save(any(EmailVerificationToken.class))).thenAnswer(invocation -> invocation.getArgument(0));

        emailVerificationService.verifyEmail("valid-token");

        verify(tokenRepository).save(argThat(token -> token.getVerifiedAt() != null));
    }
}
