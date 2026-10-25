// Input: JWT 密钥、过期时间和声明数据
// Output: Token 生成、解析和校验结果
// Pos: Auth/Token 工具层
// 维护声明: 仅维护 JWT 编解码能力；密钥策略变更请同步配置与登录流程.
package com.xiyu.bid.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.JwtParser;
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
import java.util.Optional;
import java.util.UUID;

/**
 * JWT 编解码与校验工具.
 *
 * <p>基于 HMAC-SHA256 签名；密钥需通过构造参数注入，且长度不得低于
 * {@link #MIN_SECRET_LENGTH} 字节（256 位）.</p>
 */
@Component
public class JwtUtil {

    /** 统一日志器. */
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    /** HMAC-SHA256 最小密钥字节数（256 位）. */
    private static final int MIN_SECRET_LENGTH = 32;

    /** HMAC 签名密钥（构造时由密钥字符串派生）. */
    private final SecretKey secretKey;

    /** 预构建且线程安全的 JWT 解析器（单例复用，避免每次请求重复 build 和 SPI 查找）. */
    private final JwtParser jwtParser;

    /** Token 默认过期时长（毫秒）. */
    private final long expiration;

    /**
     * 构造 JWT 工具实例.
     *
     * @param secret      HMAC 签名密钥原文（UTF-8），长度必须 ≥ 32
     * @param pExpiration Token 过期时长（毫秒）
     * @throws IllegalArgumentException 当密钥为空或长度不足时
     */
    public JwtUtil(final String secret, final long pExpiration) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalArgumentException(
                "JWT secret must be at least " + MIN_SECRET_LENGTH
                    + " characters for HMAC-SHA256. "
                    + "Current length: " + (secret != null ? secret.length() : 0)
                    + ". Please set JWT_SECRET environment variable with a strong random key."
            );
        }

        this.expiration = pExpiration;
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtParser = Jwts.parser().verifyWith(this.secretKey).build();

        log.info("JWT initialized with expiration: {} ms", pExpiration);
    }

    /**
     * 为指定用户名签发新的访问 Token（含 jti、subject、issuedAt、expiration 声明）.
     *
     * @param username Token 主体（用户标识）
     * @return 序列化后的 JWT 字符串
     */
    public String generateAccessToken(final String username) {
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

    /**
     * 为指定用户名签发 Token（当前等价于 {@link #generateAccessToken(String)}，保留兼容旧调用）.
     *
     * @param username Token 主体
     * @return 序列化后的 JWT 字符串
     */
    public String generateToken(final String username) {
        return generateAccessToken(username);
    }

    /**
     * 从 Token 中提取 subject 声明（用户名）.
     *
     * @param token JWT 字符串
     * @return 用户名；若不存在则返回空串
     */
    public String extractUsername(final String token) {
        String subject = extractAllClaims(token).getSubject();
        return subject != null ? subject : "";
    }

    /**
     * 从 Token 中提取过期时间（毫秒 epoch）.
     *
     * @param token JWT 字符串
     * @return 过期时刻的毫秒数
     */
    public Long extractExpiration(final String token) {
        return extractAllClaims(token).getExpiration().getTime();
    }

    /**
     * 从 Token 中提取 jti 声明（JWT ID）.
     *
     * @param token JWT 字符串
     * @return 含 jti 字符串的 Optional；若 token 非法或缺失则为空 Optional
     */
    public Optional<String> extractJti(final String token) {
        try {
            return Optional.ofNullable(extractAllClaims(token).getId());
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 从 Token 中提取过期时间的 {@link Instant} 形式.
     *
     * @param token JWT 字符串
     * @return 含过期 Instant 的 Optional；token 非法或缺失过期声明则为空 Optional
     */
    public Optional<Instant> extractExpirationInstant(final String token) {
        try {
            Date exp = extractAllClaims(token).getExpiration();
            return Optional.ofNullable(exp).map(Date::toInstant);
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /**
     * 校验 Token 是否属于指定用户且未过期.
     *
     * @param token    JWT 字符串
     * @param username 期望的用户名
     * @return true 当 subject 一致且未过期；任何解析/签名异常都返回 false
     */
    public boolean validateToken(final String token, final String username) {
        try {
            String extractedUsername = extractUsername(token);
            return extractedUsername.equals(username) && !isTokenExpired(token);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 解析 Token 的全部声明.
     *
     * <p>过期、签名错误、格式错误等会按异常类型透传，
     * 由调用方决定如何处理（部分场景下上层可能需要区分 401/403）.</p>
     *
     * @param token JWT 字符串
     * @return 解析后的 {@link Claims} 对象
     * @throws ExpiredJwtException     Token 已过期
     * @throws UnsupportedJwtException  不支持的 Token 格式
     * @throws MalformedJwtException    Token 格式不合法
     * @throws SecurityException        签名校验失败
     * @throws IllegalArgumentException 参数非法
     */
    public Claims extractAllClaims(final String token) {
        try {
            return this.jwtParser
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

    /**
     * 判断 Token 是否已过期.
     *
     * <p>任何解析异常均按"已过期"处理.</p>
     *
     * @param token JWT 字符串
     * @return true 当过期或解析失败
     */
    private boolean isTokenExpired(final String token) {
        try {
            Date expirationDate = extractAllClaims(token).getExpiration();
            return expirationDate == null || expirationDate.before(new Date());
        } catch (JwtException | IllegalArgumentException e) {
            return true;
        }
    }
}
