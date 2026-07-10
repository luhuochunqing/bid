package com.xiyu.bid.crm.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 用户 profile 缓存（CO-152 共享组件）。
 * <p>缓存用户的 fullName + crmSalesNo，避免 CrmAuthService 和 WebhookCrmTokenResolver 各自重复查 DB。
 * <p>TTL 5 分钟，401 / logoutUser 时主动清除。
 */
@Component
public class UserProfileCache {

    private static final long USER_PROFILE_CACHE_TTL_SECONDS = 300;

    private final UserRepository userRepository;
    private final ConcurrentMap<String, CachedUserProfile> cache = new ConcurrentHashMap<>();

    public UserProfileCache(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * 获取用户 profile，优先读缓存，未命中查 DB。
     *
     * @param username 用户名
     * @return 用户 profile（未找到时返回 empty）
     */
    public Optional<CachedUserProfile> get(String username) {
        CachedUserProfile cached = cache.get(username);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return Optional.of(cached);
        }
        return userRepository.findByUsername(username).map(u -> {
            CachedUserProfile profile = new CachedUserProfile(
                    u.getFullName(), u.getCrmSalesNo(),
                    Instant.now().plusSeconds(USER_PROFILE_CACHE_TTL_SECONDS));
            cache.put(username, profile);
            return profile;
        });
    }

    /** 清除指定用户 profile 缓存（401 / logoutUser 时调用）。 */
    public void invalidate(String username) {
        if (username != null && !username.isBlank()) {
            cache.remove(username);
        }
    }

    /**
     * 缓存的用户 profile（record 不可变，线程安全）。
     * <p>字段：fullName（昵称）、crmSalesNo（工号）、expiresAt（过期时间）
     */
    public record CachedUserProfile(String fullName, String crmSalesNo, Instant expiresAt) {}
}
