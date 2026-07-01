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
 * CRM 用户 JWT token 缓存（CO-152）。
 * <p>
 * 按 username 缓存每个用户的 CRM JWT token，实现按用户维度隔离 CRM 权限。
 * <p>
 * 缓存策略：
 * - 按 username 缓存（与 {@link OssPermissionCache} 对齐，全链路 username 串联）
 * - 默认过期时间：25 小时（对齐 CRM JWT 24h 有效期 + 1h 余量）
 * - 401 时主动清除对应用户缓存
 * - 用户登出时主动清除
 * <p>
 * 存储后端（借鉴 {@link OssPermissionCache} 的双写降级模式）：
 * - Redis 可用时，优先写 Redis（key 前缀 {@code crm:token:}），重启不丢缓存
 * - Redis 不可用时（测试 profile / Redis 宕机），降级为进程内 {@link ConcurrentHashMap}
 * <p>
 * 兼容性：
 * - 用户未配置 crm_sales_no 时，{@link CrmAuthService} 不写入本缓存，
 *   直接回退到全局共享 token（{@link #getSharedFallbackToken()} 由调用方提供）
 */
@Component
public class CrmUserTokenCache {

    private static final Logger log = LoggerFactory.getLogger(CrmUserTokenCache.class);

    static final String REDIS_KEY_PREFIX = "crm:token:";
    private static final long DEFAULT_TTL_SECONDS = 90000; // 25 小时（对齐 CRM JWT 24h + 1h 余量）
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(DEFAULT_TTL_SECONDS);

    private final Optional<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Spring 主构造：注入 Redis（可选）与共享 ObjectMapper。
     * <p>
     * 当 {@code StringRedisTemplate} Bean 存在时走 Redis；
     * 缺席时降级为进程内 Map。借鉴 {@link OssPermissionCache} 的构造模式。
     */
    @org.springframework.beans.factory.annotation.Autowired
    public CrmUserTokenCache(Optional<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 无参构造，供单元测试直接 {@code new CrmUserTokenCache()} 使用。
     * 不注入 Redis，纯内存模式。
     */
    public CrmUserTokenCache() {
        this(Optional.empty(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 写入用户的 CRM JWT token 缓存。
     *
     * @param username      用户名（缓存 key）
     * @param crmJwtToken   CRM JWT token
     * @param expiresInSeconds token 有效期（秒）
     */
    public void put(String username, String crmJwtToken, long expiresInSeconds) {
        CacheEntry entry = new CacheEntry(crmJwtToken, Instant.now().plusSeconds(expiresInSeconds));
        cache.put(username, entry);
        redisTemplate.ifPresent(t -> {
            try {
                t.opsForValue().set(redisKey(username), objectMapper.writeValueAsString(entry), DEFAULT_TTL);
            } catch (JsonProcessingException ex) {
                log.warn("CRM token Redis write failed for user={}, falling back to memory only: {}",
                        username, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("CRM token Redis write failed for user={}, falling back to memory only: {}",
                        username, ex.getMessage());
            }
        });
        log.debug("CRM token cached for user={}, expiresIn={}s", username, expiresInSeconds);
    }

    /**
     * 读取用户的 CRM JWT token。
     *
     * @param username 用户名
     * @return 命中且未过期时返回 token；未命中或已过期返回 {@link Optional#empty()}
     */
    public Optional<String> get(String username) {
        // 优先读 Redis
        if (redisTemplate.isPresent()) {
            try {
                String json = redisTemplate.get().opsForValue().get(redisKey(username));
                if (json != null) {
                    CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
                    if (Instant.now().isBefore(entry.expiresAt())) {
                        return Optional.of(entry.crmJwtToken());
                    }
                    invalidate(username); // Redis 中残留已过期条目，清理
                    return Optional.empty();
                }
            } catch (JsonProcessingException ex) {
                log.warn("CRM token Redis read failed for user={}, falling back to memory: {}",
                        username, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("CRM token Redis read failed for user={}, falling back to memory: {}",
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
        return Optional.of(entry.crmJwtToken());
    }

    /**
     * 清除指定用户的 CRM token 缓存（401 / 登出 / 修改 crmSalesNo 时调用）。
     */
    public void invalidate(String username) {
        cache.remove(username);
        redisTemplate.ifPresent(t -> {
            try {
                t.delete(redisKey(username));
            } catch (RuntimeException ex) {
                log.warn("CRM token Redis delete failed for user={}: {}", username, ex.getMessage());
            }
        });
        log.debug("CRM token cache invalidated for user={}", username);
    }

    /**
     * 清空全部用户 CRM token 缓存（admin 级别操作，谨慎使用）。
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
                log.warn("CRM token Redis clear failed: {}", ex.getMessage());
            }
        });
        log.info("CRM token cache cleared");
    }

    private String redisKey(String username) {
        return REDIS_KEY_PREFIX + username;
    }

    /**
     * 缓存条目（可序列化为 JSON 存 Redis）。
     */
    public record CacheEntry(String crmJwtToken, Instant expiresAt) {}
}
