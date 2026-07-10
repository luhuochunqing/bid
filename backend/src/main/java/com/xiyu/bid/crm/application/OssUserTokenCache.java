package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OSS 用户 token 缓存（CO-152 补齐：CRM 回调用用户身份）。
 * <p>
 * 按 username 缓存用户登录时拿到的 OSS access_token，供异步 webhook 回调链路使用。
 * <p>
 * 缓存策略：
 * - 按 username 缓存（与 {@link OssPermissionCache}/{@link CrmUserTokenCache} 对齐）
 * - 默认 TTL：1 周（用户要求：登出主动失效，未登出时尽量长可用）
 * - 用户登出时由 {@code AuthService.logout} 调 {@link #invalidate(String)} 主动清除
 * <p>
 * 存储后端（借鉴 {@link CrmUserTokenCache} 的双写降级模式）：
 * - Redis 可用时，优先写 Redis（key 前缀 {@code oss:token:}），重启不丢缓存
 * - Redis 不可用时（测试 profile / Redis 宕机），降级为进程内 {@link ConcurrentHashMap}
 * <p>
 * 关键场景：webhook 异步回调时取操作者的 OSS token 调 generateToken。
 * 用户登出后 token 不可用 → 回调失败 → 重试 1/5/15min → 死信（用户已确认接受）。
 */
@Component
public class OssUserTokenCache {

    private static final Logger log = LoggerFactory.getLogger(OssUserTokenCache.class);

    static final String REDIS_KEY_PREFIX = "oss:token:";
    /** 默认 TTL：1 周（用户要求） */
    private static final long DEFAULT_TTL_SECONDS = 604800L;
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(DEFAULT_TTL_SECONDS);

    private final Optional<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Spring 主构造：注入 Redis（可选）与共享 ObjectMapper。
     * <p>
     * 当 {@code StringRedisTemplate} Bean 存在时走 Redis；
     * 缺席时降级为进程内 Map。借鉴 {@link CrmUserTokenCache} 的构造模式。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public OssUserTokenCache(Optional<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 无参构造，供单元测试直接 {@code new OssUserTokenCache()} 使用。
     * 不注入 Redis，纯内存模式。
     */
    public OssUserTokenCache() {
        this(Optional.empty(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 写入用户的 OSS access_token 缓存。
     * <p>
     * TTL 取传入 expiresInSeconds（来自 OSS /oauth/login 响应的 expires_in）与默认 1 周中的较小值，
     * 确保缓存比 OSS token 先过期。
     *
     * @param username          用户名（缓存 key）
     * @param ossAccessToken    OSS access_token
     * @param expiresInSeconds  token 有效期（秒，来自 OSS 响应）
     */
    public void put(String username, String ossAccessToken, long expiresInSeconds) {
        // 防御：null/blank username 不缓存，避免 ConcurrentHashMap NPE
        if (username == null || username.isBlank()) {
            return;
        }
        CacheEntry entry = new CacheEntry(ossAccessToken, Instant.now().plusSeconds(expiresInSeconds));
        cache.put(username, entry);
        Duration redisTtl = Duration.ofSeconds(Math.min(expiresInSeconds, DEFAULT_TTL_SECONDS));
        redisTemplate.ifPresent(t -> {
            try {
                t.opsForValue().set(redisKey(username), objectMapper.writeValueAsString(entry), redisTtl);
            } catch (JsonProcessingException ex) {
                log.warn("OSS token Redis write failed for user={}, falling back to memory only: {}",
                        username, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("OSS token Redis write failed for user={}, falling back to memory only: {}",
                        username, ex.getMessage());
            }
        });
        log.debug("OSS token cached for user={}, expiresIn={}s", username, expiresInSeconds);
    }

    /**
     * 读取用户的 OSS access_token。
     *
     * @param username 用户名
     * @return 命中且未过期时返回 token；未命中或已过期返回 {@link Optional#empty()}
     */
    public Optional<String> get(String username) {
        // 防御：null/blank username 直接返回 empty，避免 ConcurrentHashMap NPE
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        // 优先读 Redis
        if (redisTemplate.isPresent()) {
            try {
                String json = redisTemplate.get().opsForValue().get(redisKey(username));
                if (json != null) {
                    CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
                    if (Instant.now().isBefore(entry.expiresAt())) {
                        return Optional.of(entry.ossAccessToken());
                    }
                    invalidate(username); // Redis 中残留已过期条目，清理
                    return Optional.empty();
                }
            } catch (JsonProcessingException ex) {
                log.warn("OSS token Redis read failed for user={}, falling back to memory: {}",
                        username, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("OSS token Redis read failed for user={}, falling back to memory: {}",
                        username, ex.getMessage());
            }
        }
        // 降级读内存
        CacheEntry entry = cache.get(username);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            cache.remove(username);
            return Optional.empty();
        }
        return Optional.of(entry.ossAccessToken());
    }

    /**
     * 清除指定用户的 OSS token 缓存（登出 / 401 时调用）。
     */
    public void invalidate(String username) {
        // 防御：null/blank username 跳过，避免 ConcurrentHashMap NPE
        if (username == null || username.isBlank()) {
            return;
        }
        cache.remove(username);
        redisTemplate.ifPresent(t -> {
            try {
                t.delete(redisKey(username));
            } catch (RuntimeException ex) {
                log.warn("OSS token Redis delete failed for user={}: {}", username, ex.getMessage());
            }
        });
        log.debug("OSS token cache invalidated for user={}", username);
    }

    /**
     * 清空全部用户 OSS token 缓存（admin 级别操作，谨慎使用）。
     */
    public void clear() {
        cache.clear();
        redisTemplate.ifPresent(t -> {
            try {
                ScanOptions options = ScanOptions.scanOptions()
                        .match(REDIS_KEY_PREFIX + "*")
                        .count(100)
                        .build();
                try (Cursor<String> cursor = t.scan(options)) {
                    while (cursor.hasNext()) {
                        t.delete(cursor.next());
                    }
                }
            } catch (RuntimeException ex) {
                log.warn("OSS token Redis clear failed: {}", ex.getMessage());
            }
        });
        log.info("OSS token cache cleared");
    }

    private String redisKey(String username) {
        return REDIS_KEY_PREFIX + username;
    }

    /**
     * 缓存条目（可序列化为 JSON 存 Redis）。
     */
    public record CacheEntry(String ossAccessToken, Instant expiresAt) {}
}
