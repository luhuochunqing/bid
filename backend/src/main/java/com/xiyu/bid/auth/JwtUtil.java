// Input: JWT 密钥、过期时间和声明数据
// Output: Token 生成、解析和校验结果
// Pos: Auth/Token 工具层
// 维护声明: 仅维护 JWT 编解码能力；密钥策略变更请同步配置与登录流程.
package com.xiyu.bid.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SecurityException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);
    private static final int MIN_SECRET_LENGTH = 32; // 256 bits = 32 bytes

    private final SecretKey secretKey;
    private final long expiration;

    public JwtUtil(String secret, long pExpiration) {
        // 验证密钥长度
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                "JWT secret must be at least " + MIN_SECRET_LENGTH + " characters for HMAC-SHA256. " +
                "Current length: " + (secret != null ? secret.length() : 0) + ". " +
                "Please set JWT_SECRET environment variable with a strong random key."
            );
        }

        this.expiration = pExpiration;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        log.info("JWT initialized with expiration: {} ms", pExpiration);
    }

    public String generateAccessToken(String username) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(secretKey)
                .compact();
    }

    public String generateToken(String username) {
        return generateAccessToken(username);
    }

    public String extractUsername(String token) {
        String subject = extractAllClaims(token).getSubject();
        return subject != null ? subject : "";
    }

    public Long extractExpiration(String token) {
        return extractAllClaims(token).getExpiration().getTime();
    }

    public String extractJti(String token) {
        try {
            return extractAllClaims(token).getId();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public Instant extractExpirationInstant(String token) {
        try {
            Date exp = extractAllClaims(token).getExpiration();
            return exp == null ? null : exp.toInstant();
        } catch (JwtException | IllegalArgumentException e) {
            return null;
        }
    }

    public boolean validateToken(String token, String username) {
        try {
            String extractedUsername = extractUsername(token);
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    public Claims extractAllClaims(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (ExpiredJwtException e) {
            log.debug("JWT token is expired: {}", e.getMessage());
            throw e;
        } catch (UnsupportedJwtException e) {
            log.debug("JWT token is unsupported: {}", e.getMessage());
            throw e;
        } catch (MalformedJwtException e) {
            log.debug("JWT token is malformed: {}", e.getMessage());
            throw e;
        } catch (SecurityException e) {
            log.debug("JWT token signature is invalid: {}", e.getMessage());
            throw e;
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is invalid: {}", e.getMessage());
            throw e;
        }
    }

    private boolean isTokenExpired(String token) {
        try {
            Date expirationDate = extractAllClaims(token).getExpiration();
            return expirationDate == null || expirationDate.before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
}
