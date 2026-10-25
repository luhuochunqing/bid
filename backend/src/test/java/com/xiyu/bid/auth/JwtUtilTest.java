package com.xiyu.bid.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef"; // 32 chars
    private static final long EXPIRATION_MS = 3600_000L;

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil(SECRET, EXPIRATION_MS);
    }

    @Test
    @DisplayName("构造函数：密钥长度不足32字节应抛出 IllegalArgumentException")
    void constructor_shouldThrowWhenSecretTooShort() {
        assertThatThrownBy(() -> new JwtUtil("short-secret", EXPIRATION_MS))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 characters");
    }

    @Test
    @DisplayName("生成与提取：正常签发后应能提取正确的 username 和 claims")
    void generateAndExtract_shouldWorkNormally() {
        String token = jwtUtil.generateAccessToken("testUser");
        assertThat(token).isNotBlank();

        String username = jwtUtil.extractUsername(token);
        assertThat(username).isEqualTo("testUser");

        Claims claims = jwtUtil.extractAllClaims(token);
        assertThat(claims.getSubject()).isEqualTo("testUser");

        boolean isValid = jwtUtil.validateToken(token, "testUser");
        assertThat(isValid).isTrue();

        boolean isInvalidUser = jwtUtil.validateToken(token, "anotherUser");
        assertThat(isInvalidUser).isFalse();
    }

    @Test
    @DisplayName("异常处理：非法格式 token 应抛出 MalformedJwtException")
    void extractAllClaims_shouldThrowOnMalformedToken() {
        assertThatThrownBy(() -> jwtUtil.extractAllClaims("invalid.token.string"))
                .isInstanceOf(MalformedJwtException.class);
    }

    @Test
    @DisplayName("过期处理：过期 token 校验应返回 false 或抛出 ExpiredJwtException")
    void validateToken_shouldReturnFalseForExpiredToken() {
        JwtUtil shortLivedJwtUtil = new JwtUtil(SECRET, -1000L);
        String expiredToken = shortLivedJwtUtil.generateAccessToken("expiredUser");

        boolean isValid = shortLivedJwtUtil.validateToken(expiredToken, "expiredUser");
        assertThat(isValid).isFalse();

        assertThatThrownBy(() -> shortLivedJwtUtil.extractAllClaims(expiredToken))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("并发安全：多线程并发解析不应抛出 NoSuchElementException 或产生竞态")
    void extractAllClaims_concurrentParsing_shouldBeThreadSafe() throws Exception {
        int threadCount = 30;
        int iterationsPerThread = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tokens.add(jwtUtil.generateAccessToken("user_" + i));
        }

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            final String token = tokens.get(i);
            final String expectedUser = "user_" + i;
            tasks.add(() -> {
                for (int iter = 0; iter < iterationsPerThread; iter++) {
                    Claims claims = jwtUtil.extractAllClaims(token);
                    assertThat(claims.getSubject()).isEqualTo(expectedUser);
                    assertThat(jwtUtil.extractUsername(token)).isEqualTo(expectedUser);
                }
                return null;
            });
        }

        try {
            List<Future<Void>> futures = executor.invokeAll(tasks);
            for (Future<Void> future : futures) {
                // 如果有任何异常（例如 NoSuchElementException），在此处抛出
                future.get();
            }
        } finally {
            executor.shutdown();
        }
    }
}
