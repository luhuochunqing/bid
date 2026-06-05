package com.xiyu.bid.repository;

import com.xiyu.bid.entity.EmailVerificationToken;
import com.xiyu.bid.entity.RoleProfile;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for EmailVerificationTokenRepository
 */
@DataJpaTest
@ActiveProfiles("test")
class EmailVerificationTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmailVerificationTokenRepository repository;

    private RoleProfile defaultProfile;

    @BeforeEach
    void setUp() {
        defaultProfile = RoleProfile.builder()
                .code("test-profile")
                .name("测试权限")
                .dataScope("self")
                .build();
        entityManager.persist(defaultProfile);
    }

    @Test
    void testFindByToken_Success() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .password("password")
                .role(User.Role.STAFF)
                .roleProfile(defaultProfile)
                .emailVerified(false)
                .enabled(true)
                .build();
        entityManager.persist(user);

        EmailVerificationToken token = EmailVerificationToken.builder()
                .token("test-token-hash")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        entityManager.persist(token);
        entityManager.flush();

        Optional<EmailVerificationToken> found = repository.findByToken("test-token-hash");

        assertTrue(found.isPresent());
        assertEquals("test-token-hash", found.get().getToken());
    }

    @Test
    void testFindByToken_NotFound() {
        Optional<EmailVerificationToken> found = repository.findByToken("nonexistent-token");

        assertFalse(found.isPresent());
    }

    @Test
    void testFindActiveByUser_Success() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .password("password")
                .role(User.Role.STAFF)
                .roleProfile(defaultProfile)
                .emailVerified(false)
                .enabled(true)
                .build();
        entityManager.persist(user);

        EmailVerificationToken activeToken = EmailVerificationToken.builder()
                .token("active-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        entityManager.persist(activeToken);

        entityManager.flush();

        Optional<EmailVerificationToken> found = repository.findActiveByUser(user, LocalDateTime.now());

        assertTrue(found.isPresent());
        assertEquals("active-token", found.get().getToken());
    }

    @Test
    void testFindActiveByUser_ExcludesVerified() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .password("password")
                .role(User.Role.STAFF)
                .roleProfile(defaultProfile)
                .emailVerified(false)
                .enabled(true)
                .build();
        entityManager.persist(user);

        EmailVerificationToken verifiedToken = EmailVerificationToken.builder()
                .token("verified-token")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .verifiedAt(LocalDateTime.now())
                .build();
        entityManager.persist(verifiedToken);

        entityManager.flush();

        Optional<EmailVerificationToken> found = repository.findActiveByUser(user, LocalDateTime.now());

        assertFalse(found.isPresent());
    }

    @Test
    void testFindActiveByUser_ExcludesExpired() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .password("password")
                .role(User.Role.STAFF)
                .roleProfile(defaultProfile)
                .emailVerified(false)
                .enabled(true)
                .build();
        entityManager.persist(user);

        EmailVerificationToken expiredToken = EmailVerificationToken.builder()
                .token("expired-token")
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        entityManager.persist(expiredToken);

        entityManager.flush();

        Optional<EmailVerificationToken> found = repository.findActiveByUser(user, LocalDateTime.now());

        assertFalse(found.isPresent());
    }

    @Test
    void testDeleteExpiredTokens() {
        User user = User.builder()
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .password("password")
                .role(User.Role.STAFF)
                .roleProfile(defaultProfile)
                .emailVerified(false)
                .enabled(true)
                .build();
        entityManager.persist(user);

        EmailVerificationToken expiredToken1 = EmailVerificationToken.builder()
                .token("expired1")
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(1))
                .build();
        entityManager.persist(expiredToken1);

        EmailVerificationToken expiredToken2 = EmailVerificationToken.builder()
                .token("expired2")
                .user(user)
                .expiresAt(LocalDateTime.now().minusHours(2))
                .build();
        entityManager.persist(expiredToken2);

        EmailVerificationToken activeToken = EmailVerificationToken.builder()
                .token("active")
                .user(user)
                .expiresAt(LocalDateTime.now().plusHours(24))
                .build();
        entityManager.persist(activeToken);

        entityManager.flush();

        int deletedCount = repository.deleteExpiredTokens(LocalDateTime.now());

        assertEquals(2, deletedCount);

        List<EmailVerificationToken> remaining = repository.findAll();
        assertEquals(1, remaining.size());
        assertEquals("active", remaining.get(0).getToken());
    }
}
