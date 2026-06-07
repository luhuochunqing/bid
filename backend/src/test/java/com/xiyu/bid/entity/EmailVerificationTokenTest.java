package com.xiyu.bid.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for EmailVerificationToken entity
 * Tests cover: entity creation, field validation, lifecycle callbacks
 */
class EmailVerificationTokenTest {

    @Test
    void testEntityCreation() {
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .build();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(1L)
                .token("test-token-hash")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        assertNotNull(token);
        assertEquals(1L, token.getId());
        assertEquals("test-token-hash", token.getToken());
        assertEquals(user, token.getUser());
        assertNotNull(token.getExpiresAt());
    }

    @Test
    void testVerifiedAtInitiallyNull() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(1L)
                .token("test-token-hash")
                .user(User.builder().id(1L).build())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        assertNull(token.getVerifiedAt());
    }

    @Test
    void testCanSetVerifiedAt() {
        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(1L)
                .token("test-token-hash")
                .user(User.builder().id(1L).build())
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();

        LocalDateTime verifiedTime = LocalDateTime.now();
        token.setVerifiedAt(verifiedTime);

        assertEquals(verifiedTime, token.getVerifiedAt());
    }

    @Test
    void testTokenExpiration() {
        LocalDateTime expiredTime = LocalDateTime.now().minusHours(1);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(1L)
                .token("test-token-hash")
                .user(User.builder().id(1L).build())
                .expiresAt(expiredTime)
                .build();

        assertTrue(token.getExpiresAt().isBefore(LocalDateTime.now()));
    }

    @Test
    void testTokenNotExpired() {
        LocalDateTime validTime = LocalDateTime.now().plusHours(24);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(1L)
                .token("test-token-hash")
                .user(User.builder().id(1L).build())
                .expiresAt(validTime)
                .build();

        assertTrue(token.getExpiresAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void testBuilderPattern() {
        User user = User.builder()
                .id(1L)
                .username("testuser")
                .build();

        EmailVerificationToken token = EmailVerificationToken.builder()
                .id(1L)
                .token("hashed-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .verifiedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .build();

        assertNotNull(token);
        assertEquals(1L, token.getId());
        assertEquals("hashed-token", token.getToken());
        assertEquals(user, token.getUser());
        assertNotNull(token.getVerifiedAt());
        assertNotNull(token.getCreatedAt());
    }

    @Test
    void testNoArgsConstructor() {
        EmailVerificationToken token = new EmailVerificationToken();

        assertNotNull(token);
        assertNull(token.getId());
        assertNull(token.getToken());
        assertNull(token.getUser());
        assertNull(token.getExpiresAt());
        assertNull(token.getVerifiedAt());
        assertNull(token.getCreatedAt());
    }

    @Test
    void testAllArgsConstructor() {
        User user = User.builder().id(1L).build();
        LocalDateTime now = LocalDateTime.now();

        EmailVerificationToken token = new EmailVerificationToken(
                1L, "token", user, now.plusHours(24), now, now
        );

        assertEquals(1L, token.getId());
        assertEquals("token", token.getToken());
        assertEquals(user, token.getUser());
        assertEquals(now.plusHours(24), token.getExpiresAt());
        assertEquals(now, token.getVerifiedAt());
        assertEquals(now, token.getCreatedAt());
    }
}
