package com.xiyu.bid.service;

import com.xiyu.bid.entity.PasswordResetToken;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.PasswordResetTokenRepository;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;

/**
 * 密码重置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

        private final PasswordResetTokenRepository tokenRepository;
        private final UserRepository userRepository;
        private final PasswordEncoder passwordEncoder;
        private final EmailService emailService;

        private static final int TOKEN_EXPIRATION_HOURS = 1;

        /**
         * 创建并发送密码重置令牌
         */
        @Transactional
        public String createPasswordResetToken(String email) {
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new IllegalArgumentException("User not found with email: " + email));

                if (!user.getEnabled()) {
                        throw new IllegalStateException("User account is disabled");
                }

                // 生成原始令牌
                String rawToken = UUID.randomUUID().toString();
                String hashedToken = hashToken(rawToken);

                // 设置过期时间
                LocalDateTime expiresAt = LocalDateTime.now().plusHours(TOKEN_EXPIRATION_HOURS);

                // 保存令牌
                PasswordResetToken resetToken = PasswordResetToken.builder()
                        .token(hashedToken)
                        .user(user)
                        .expiresAt(expiresAt)
                        .build();

                tokenRepository.save(resetToken);

                // 发送邮件（开发环境返回原始令牌）
                String result = emailService.sendPasswordResetEmail(email, rawToken);

                log.info("Password reset token created for user: {}", user.getUsername());
                return result;
        }

        /**
         * 验证重置令牌
         */
        public boolean validateToken(String rawToken) {
                String hashedToken = hashToken(rawToken);

                return tokenRepository.findByToken(hashedToken)
                        .filter(token -> token.getUsedAt() == null)
                        .filter(token -> token.getExpiresAt().isAfter(LocalDateTime.now()))
                        .isPresent();
        }

        /**
         * 重置密码
         */
        @Transactional
        public void resetPassword(String rawToken, String newPassword) {
                String hashedToken = hashToken(rawToken);

                PasswordResetToken resetToken = tokenRepository.findByToken(hashedToken)
                        .orElseThrow(() -> new IllegalArgumentException("Invalid or expired token"));

                if (resetToken.getUsedAt() != null) {
                        throw new IllegalStateException("Token has already been used");
                }

                if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                        throw new IllegalStateException("Token has expired");
                }

                // 更新密码
                User user = resetToken.getUser();
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);

                // 标记令牌已使用
                tokenRepository.markAsUsed(hashedToken, LocalDateTime.now());

                log.info("Password reset successfully for user: {}", user.getUsername());
        }

        /**
         * 清理过期或已使用的令牌
         */
        @Transactional
        public void cleanupExpiredTokens() {
                int deleted = tokenRepository.deleteExpiredOrUsedTokens(LocalDateTime.now());
                log.info("Cleaned up {} expired/used password reset tokens", deleted);
        }

        /**
         * 使用SHA-256哈希令牌
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
