package com.xiyu.bid.auth;

import org.springframework.security.authentication.InsufficientAuthenticationException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Refresh token 生成与 SHA-256 哈希的纯函数工具类。
 * <p>提取自 AuthService，使 AuthService 保持在 FP-Java 300 行限制内。
 */
public final class AuthTokenHasher {

    private AuthTokenHasher() {
    }

    /** 生成随机 refresh token（两个去横线 UUID 拼接）。 */
    public static String generate() {
        return UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "");
    }

    /** 对 refresh token 做 SHA-256 哈希（Hex 小写）。 */
    public static String hash(String token) {
        if (token == null || token.isBlank()) {
            throw new InsufficientAuthenticationException("Refresh token is invalid");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 digest unavailable", ex);
        }
    }
}
