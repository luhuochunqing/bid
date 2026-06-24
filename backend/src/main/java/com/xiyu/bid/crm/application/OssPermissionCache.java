package com.xiyu.bid.crm.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * OSS 用户权限缓存。
 * <p>
 * 缓存策略：
 * - 按 username 缓存
 * - 默认过期时间：30 分钟
 * - 登出时主动清除
 * <p>
 * 缓存内容：
 * - roleCode：从 OSS 实时抓取的内部角色码
 * - menuPermissions：从 OSS 实时抓取并映射后的内部菜单权限码列表
 * - permission：原始 OSS 权限响应（备用）
 */
@Component
public class OssPermissionCache {

    private static final Logger log = LoggerFactory.getLogger(OssPermissionCache.class);

    private static final long DEFAULT_TTL_SECONDS = 28800; // 8 小时

    private final ConcurrentMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /**
     * 写入完整的角色+权限缓存（登录时调用）。
     */
    public void put(String username, String roleCode, List<String> menuPermissions, CrmUserPermission permission) {
        cache.put(username, new CacheEntry(roleCode, menuPermissions, permission,
                Instant.now().plusSeconds(DEFAULT_TTL_SECONDS)));
        log.debug("OSS permission cached for user={}, roleCode={}, menuPermissions={}",
                username, roleCode, menuPermissions == null ? 0 : menuPermissions.size());
    }

    /**
     * 旧版兼容方法：只缓存 CrmUserPermission（不缓存角色+菜单权限）。
     * 用于 CrmPermissionService 等内部缓存，避免重复请求 OSS 接口。
     * key 可能是 token hash（非 username），不会与登录缓存冲突。
     */
    public void put(String key, CrmUserPermission permission) {
        cache.put(key, new CacheEntry(null, List.of(), permission,
                Instant.now().plusSeconds(DEFAULT_TTL_SECONDS)));
        log.debug("OSS permission cached (legacy) for key={}", key);
    }

    /**
     * 获取完整缓存条目。
     */
    public Optional<CacheEntry> getEntry(String username) {
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
        log.debug("OSS permission cache invalidated for user={}", username);
    }

    public void clear() {
        cache.clear();
        log.info("OSS permission cache cleared");
    }

    public int size() {
        return cache.size();
    }

    public record CacheEntry(
            String roleCode,
            List<String> menuPermissions,
            CrmUserPermission permission,
            Instant expiresAt
    ) {}
}
