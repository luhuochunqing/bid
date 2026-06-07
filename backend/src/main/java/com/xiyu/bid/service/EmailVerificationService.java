package com.xiyu.bid.service;

import com.xiyu.bid.entity.EmailVerificationToken;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.EmailVerificationTokenRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Service for email verification
 * Handles creation and validation of email verification tokens
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private final EmailVerificationTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    private static final int TOKEN_EXPIRATION_HOURS = 24;

    /**
     * Create and send an email verification token
     *
     * @param userId the user ID
     * @return the result message from the email service
     * @throws IllegalArgumentException if user not found
     * @throws IllegalStateException if email is already verified
     */
    @Transactional
    public String createVerificationToken(Long userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (Boolean.TRUE.equals(user.getEmailVerified())) {
            throw new IllegalStateException("Email is already verified");
        }

        // Generate a random token
        String rawToken = UUID.randomUUID().toString();
        String hashedToken = hashToken(rawToken);

        LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRATION_HOURS);

        EmailVerificationToken token = EmailVerificationToken.builder()
            .token(hashedToken)
            .user(user)
            .expiresAt(expiresAt)
            .build();

        tokenRepository.save(token);

        String result = emailService.sendVerificationEmail(user.getEmail(), rawToken);

        log.info("Email verification token created for user: {}", user.getUsername());
        return result;
    }

    /**
     * Verify an email using the token
     *
     * @param rawToken the raw verification token
     * @throws IllegalArgumentException if token is invalid or expired
     * @throws IllegalStateException if token has already been used
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        String hashedToken = hashToken(rawToken);

        EmailVerificationToken token = tokenRepository.findByToken(hashedToken)
            .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

        if (token.getVerifiedAt() != null) {
            throw new IllegalStateException("Token has already been used");
        }

        if (token.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new IllegalStateException("Token has expired");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.setVerifiedAt(LocalDateTime.now());
        tokenRepository.save(token);

        log.info("Email verified for user: {}", user.getUsername());
    }

    /**
     * Hash a token using SHA-256
     *
     * @param token the raw token
     * @return the hex-encoded hash
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
