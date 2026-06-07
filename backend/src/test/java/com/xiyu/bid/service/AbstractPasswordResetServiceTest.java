package com.xiyu.bid.service;

import com.xiyu.bid.entity.PasswordResetToken;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.PasswordResetTokenRepository;
import com.xiyu.bid.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;

@ExtendWith(MockitoExtension.class)
abstract class AbstractPasswordResetServiceTest {

    @Mock
    protected PasswordResetTokenRepository tokenRepository;

    @Mock
    protected UserRepository userRepository;

    @Mock
    protected PasswordEncoder passwordEncoder;

    @Mock
    protected EmailService emailService;

    protected PasswordResetService passwordResetService;
    protected User testUser;
    protected PasswordResetToken testToken;

    @BeforeEach
    void setUpPasswordResetFixture() {
        passwordResetService = new PasswordResetService(
                tokenRepository,
                userRepository,
                passwordEncoder,
                emailService
        );

        testUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .fullName("Test User")
                .role(User.Role.STAFF)
                .enabled(true)
                .build();

        testToken = PasswordResetToken.builder()
                .id(1L)
                .token("hashedToken")
                .user(testUser)
                .expiresAt(LocalDateTime.now().plusHours(1))
                .createdAt(LocalDateTime.now())
                .build();
    }

    protected String hashTokenForTest(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
