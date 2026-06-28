package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.xiyu.bid.security.RoleCodeCachePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OSS 用户权限缓存。
 * <p>
 * 缓存策略：
 * - 按 username 缓存
 * - 默认过期时间：25 小时（对齐 JWT 24h 有效期 + 1h 余量，避免缓存过期但 JWT 仍有效导致 fail-closed 403）
 * - 登出时主动清除
 * <p>
 * 存储后端：
 * - Redis 可用时，优先写 Redis（key 前缀 {@code oss:perm:}），重启不丢缓存。
 *   解决后端重启导致 OSS 用户旧 JWT（24h 有效）失效、fail-closed 403 的问题。
 * - Redis 不可用时（测试 profile / Redis 宕机），降级为进程内 {@link ConcurrentHashMap}，
 *   行为同改造前。
 * <p>
 * 缓存内容：
 * - roleCode：从 OSS 实时抓取的内部角色码
 * - menuPermissions：从 OSS 实时抓取并映射后的内部菜单权限码列表
 * - permission：原始 OSS 权限响应（备用）
 */
@Component
public class OssPermissionCache implements RoleCodeCachePort {

    private static final Logger log = LoggerFactory.getLogger(OssPermissionCache.class);

    static final String REDIS_KEY_PREFIX = "oss:perm:";
    private static final long DEFAULT_TTL_SECONDS = 90000; // 25 小时（对齐 JWT 24h + 1h 余量，CO-373 消除 fail-closed 403 窗口期）
    private static final Duration DEFAULT_TTL = Duration.ofSeconds(DEFAULT_TTL_SECONDS);

    private final Optional<StringRedisTemplate> redisTemplate;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * Spring 主构造：注入 Redis（可选）与共享 ObjectMapper。
     * <p>
     * 当 {@code StringRedisTemplate} Bean 存在（非 test profile）时走 Redis；
     * 缺席时降级为进程内 Map。
     */
    public OssPermissionCache(Optional<StringRedisTemplate> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 无参构造，供单元测试直接 {@code new OssPermissionCache()} 使用。
     * 不注入 Redis，纯内存模式。
     */
    public OssPermissionCache() {
        this(Optional.empty(), new ObjectMapper().registerModule(new JavaTimeModule()));
    }

    /**
     * 写入完整的角色+权限缓存（登录时调用）。
     */
    public void put(String username, String roleCode, List<String> menuPermissions, CrmUserPermission permission) {
        CacheEntry entry = new CacheEntry(roleCode, menuPermissions, permission,
                Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
        cache.put(username, entry);
        redisTemplate.ifPresent(t -> {
            try {
                t.opsForValue().set(redisKey(username), objectMapper.writeValueAsString(entry), DEFAULT_TTL);
            } catch (JsonProcessingException ex) {
                log.warn("OSS permission Redis write failed for user={}, falling back to memory only: {}",
                        username, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("OSS permission Redis write failed for user={}, falling back to memory only: {}",
                        username, ex.getMessage());
            }
        });
        log.debug("OSS permission cached for user={}, roleCode={}, menuPermissions={}",
                username, roleCode, menuPermissions == null ? 0 : menuPermissions.size());
    }

    /**
     * 旧版兼容方法：只缓存 CrmUserPermission（不缓存角色+菜单权限）。
     * 用于 CrmPermissionService 等内部缓存，避免重复请求 OSS 接口。
     * key 可能是 token hash（非 username），不会与登录缓存冲突。
     */
    public void put(String key, CrmUserPermission permission) {
        CacheEntry entry = new CacheEntry(null, List.of(), permission,
                Instant.now().plusSeconds(DEFAULT_TTL_SECONDS));
        cache.put(key, entry);
        redisTemplate.ifPresent(t -> {
            try {
                t.opsForValue().set(redisKey(key), objectMapper.writeValueAsString(entry), DEFAULT_TTL);
            } catch (JsonProcessingException ex) {
                log.warn("OSS permission Redis write (legacy) failed for key={}: {}", key, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("OSS permission Redis write (legacy) failed for key={}: {}", key, ex.getMessage());
            }
        });
        log.debug("OSS permission cached (legacy) for key={}", key);
    }

    /**
     * 获取完整缓存条目。
     */
    public Optional<CacheEntry> getEntry(String username) {
        // 优先读 Redis
        if (redisTemplate.isPresent()) {
            try {
                String json = redisTemplate.get().opsForValue().get(redisKey(username));
                if (json != null) {
                    CacheEntry entry = objectMapper.readValue(json, CacheEntry.class);
                    if (Instant.now().isBefore(entry.expiresAt())) {
                        return Optional.of(entry);
                    }
                    invalidate(username); // Redis 中残留已过期条目，清理
                    return Optional.empty();
                }
            } catch (JsonProcessingException ex) {
                log.warn("OSS permission Redis read failed for user={}, falling back to memory: {}",
                        username, ex.getMessage());
            } catch (RuntimeException ex) {
                log.warn("OSS permission Redis read failed for user={}, falling back to memory: {}",
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
        return Optional.of(entry);
    }

    /**
     * 获取缓存的内部角色码。
     */
    public Optional<String> getRoleCode(String username) {
        return getEntry(username).map(CacheEntry::roleCode);
    }

    /**
     * 获取缓存的菜单权限码列表。
     */
    public Optional<List<String>> getMenuPermissions(String username) {
        return getEntry(username).map(CacheEntry::menuPermissions);
    }

    /**
     * 获取缓存的原始 OSS 权限响应。
     */
    public Optional<CrmUserPermission> get(String username) {
        return getEntry(username).map(CacheEntry::permission);
    }

    public void invalidate(String username) {
        cache.remove(username);
        redisTemplate.ifPresent(t -> {
            try {
                t.delete(redisKey(username));
            } catch (RuntimeException ex) {
                log.warn("OSS permission Redis delete failed for user={}: {}", username, ex.getMessage());
            }
        });
        log.debug("OSS permission cache invalidated for user={}", username);
    }

    /**
     * 清空全部缓存。
     * <p>
     * Redis 模式下用 SCAN 删除 {@code oss:perm:*} 前缀 key，不影响其他 key
     * （{@code revoked:jwt:} / {@code personnel:export:} 等）。
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
                log.warn("OSS permission Redis clear failed: {}", ex.getMessage());
            }
        });
        log.info("OSS permission cache cleared");
    }

    public int size() {
        return cache.size();
    }

    private String redisKey(String key) {
        return REDIS_KEY_PREFIX + key;
    }

    public record CacheEntry(
            String roleCode,
            List<String> menuPermissions,
            CrmUserPermission permission,
            Instant expiresAt
    ) {}
}
