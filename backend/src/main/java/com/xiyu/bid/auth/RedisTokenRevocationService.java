// Input: StringRedisTemplate、jti、过期时间
// Output: Redis 中的撤销标记
// Pos: Auth/Token 撤销层
// 维护声明: 仅维护 Redis 实现；e2e 与 Redis 缺席场景请用 InMemoryTokenRevocationService.
package com.xiyu.bid.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.time.Instant;

@Slf4j
public class RedisTokenRevocationService implements TokenRevocationService {

    private static final String KEY_PREFIX = "revoked:jwt:";

    private final StringRedisTemplate redisTemplate;

    public RedisTokenRevocationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return;
        }
        Duration ttl = ttlFromExpiry(expiresAt);
        if (ttl.isZero() || ttl.isNegative()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        } catch (RuntimeException ex) {
            log.warn("Failed to record JWT revocation in Redis (jti={}): {}", jti, ex.getMessage());
        }
    }

    @Override
    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(KEY_PREFIX + jti);
            return Boolean.TRUE.equals(exists);
        } catch (RuntimeException ex) {
            log.warn("Failed to query JWT revocation from Redis (jti={}): {}", jti, ex.getMessage());
            return false;
        }
    }

    private Duration ttlFromExpiry(Instant expiresAt) {
        if (expiresAt == null) {
            return Duration.ofHours(24);
        }
        Duration remaining = Duration.between(Instant.now(), expiresAt);
        return remaining.isNegative() ? Duration.ZERO : remaining;
    }
}
