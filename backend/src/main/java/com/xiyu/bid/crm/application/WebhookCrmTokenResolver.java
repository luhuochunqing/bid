package com.xiyu.bid.crm.application;

import com.xiyu.bid.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Webhook 场景的 CRM token 解析器（CO-152 补齐）。
 * <p>
 * 与 {@link CrmAuthService#getValidTokenForUser(String)} 区别：
 * - getValidTokenForUser 在用户 OSS token 不可用时回退到全局共享 token
 * - 本类在用户 OSS token 不可用时抛异常（webhook 场景要求严格按用户身份）
 * <p>
 * 用户登出 / token 过期 → 抛异常 → webhook 重试 1/5/15min → 死信（用户已确认接受）。
 */
@Component
public class WebhookCrmTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(WebhookCrmTokenResolver.class);

    private final CrmAuthService crmAuthService;
    private final OssUserTokenCache ossUserTokenCache;
    private final CrmUserTokenCache userTokenCache;
    private final UserRepository userRepository;
    private final ConcurrentMap<String, CachedUserProfile> userProfileCache = new ConcurrentHashMap<>();

    private record CachedUserProfile(String fullName, String crmSalesNo, Instant expiresAt) {}
    private static final long USER_PROFILE_CACHE_TTL_SECONDS = 300;

    public WebhookCrmTokenResolver(CrmAuthService crmAuthService,
                                   OssUserTokenCache ossUserTokenCache,
                                   CrmUserTokenCache userTokenCache,
                                   UserRepository userRepository) {
        this.crmAuthService = crmAuthService;
        this.ossUserTokenCache = ossUserTokenCache;
        this.userTokenCache = userTokenCache;
        this.userRepository = userRepository;
    }

    /**
     * 获取指定用户的 CRM JWT token，强制使用该用户的 OSS token。
     *
     * @param username 操作者 username（来自 webhook_delivery_tasks.operator_username）
     * @return 该用户的 CRM JWT token
     * @throws IllegalStateException 用户 OSS token 不可用或 generateToken 失败
     */
    public String getValidTokenForUserStrict(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalStateException("Cannot get strict user token: username is empty");
        }
        CachedUserProfile p = getCachedUserProfile(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot get strict user token: user not found, username=" + username));
        String salesNo = (p.crmSalesNo() != null && !p.crmSalesNo().isBlank()) ? p.crmSalesNo() : username;
        String nickName = (p.fullName() != null && !p.fullName().isBlank()) ? p.fullName() : username;
        // 先查 CRM JWT 缓存
        return userTokenCache.get(username)
                .orElseGet(() -> fetchAndCacheUserToken(username, nickName, salesNo));
    }

    private String fetchAndCacheUserToken(String username, String nickName, String salesNo) {
        // 用用户 OSS token 调 generateToken（不回退全局）
        String ossToken = ossUserTokenCache.get(username)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot get strict user token: user OSS token not found or expired, username=" + username
                                + " (user may have logged out or token expired)"));
        String crmJwt = crmAuthService.applyCrmTokenWithOssToken(ossToken, nickName, salesNo);
        long ttlSeconds = JwtTtlResolver.resolveTtlSeconds(crmJwt);
        userTokenCache.put(username, crmJwt, ttlSeconds);
        return crmJwt;
    }

    /** 401 时清除该用户的 CRM JWT + profile 缓存。 */
    public void handleUnauthorizedForUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.remove(username);
            log.info("Webhook CRM JWT token cache cleared for user={} due to 401", username);
        }
    }

    private Optional<CachedUserProfile> getCachedUserProfile(String username) {
        CachedUserProfile cached = userProfileCache.get(username);
        if (cached != null && Instant.now().isBefore(cached.expiresAt())) {
            return Optional.of(cached);
        }
        return userRepository.findByUsername(username).map(u -> {
            CachedUserProfile profile = new CachedUserProfile(
                    u.getFullName(), u.getCrmSalesNo(),
                    Instant.now().plusSeconds(USER_PROFILE_CACHE_TTL_SECONDS));
            userProfileCache.put(username, profile);
            return profile;
        });
    }
}
