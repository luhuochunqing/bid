// Input: jti 风格的幂等键、序列化的响应
// Output: Redis 短期缓存项
// Pos: Idempotency/Redis 实现
// 维护声明: 仅维护 Redis 实现；Redis 缺席时由 InMemoryIdempotencyStore 兜底。
package com.xiyu.bid.idempotency;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Optional;

@Slf4j
@Component
@Primary
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisIdempotencyStore implements IdempotencyStore {

    private static final String KEY_PREFIX = "idem:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisIdempotencyStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<CachedResponse> find(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        try {
            String raw = redisTemplate.opsForValue().get(KEY_PREFIX + key);
            if (raw == null) {
                return Optional.empty();
            }
            Envelope env = objectMapper.readValue(raw, Envelope.class);
            byte[] body = env.body == null ? new byte[0] : Base64.getDecoder().decode(env.body);
            return Optional.of(new CachedResponse(env.status, env.contentType, body, env.requestBodyHash));
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Failed to read idempotency cache (key={}): {}", key, ex.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(String key, CachedResponse response, Duration ttl) {
        if (key == null || key.isBlank() || response == null) {
            return;
        }
        try {
            Envelope env = new Envelope(
                    response.getStatus(),
                    response.getContentType(),
                    Base64.getEncoder().encodeToString(response.getBody()),
                    response.getRequestBodyHash()
            );
            String payload = objectMapper.writeValueAsString(env);
            redisTemplate.opsForValue().set(
                    KEY_PREFIX + key,
                    payload,
                    ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofMinutes(10) : ttl
            );
        } catch (RuntimeException | java.io.IOException ex) {
            log.warn("Failed to write idempotency cache (key={}): {}", key, ex.getMessage());
        }
    }

    public static final class Envelope {
        public final int status;
        public final String contentType;
        public final String body;
        public final String requestBodyHash;

        @JsonCreator
        public Envelope(
                @JsonProperty("status") int status,
                @JsonProperty("contentType") String contentType,
                @JsonProperty("body") String body,
                @JsonProperty("requestBodyHash") String requestBodyHash
        ) {
            this.status = status;
            this.contentType = contentType;
            this.body = body;
            this.requestBodyHash = requestBodyHash;
        }
    }

    @SuppressWarnings("unused")
    private static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }
}
