package com.xiyu.bid.crm.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook 场景的 CRM token 解析器（CO-152 补齐）。
 * <p>
 * 与 {@link CrmAuthService#getValidTokenForUser(String)} 区别：
 * - getValidTokenForUser 在用户 OSS token 不可用时回退到全局共享 token
 * - 本类在用户 OSS token 不可用时抛 {@link TokenUnavailableException}（webhook 场景要求严格按用户身份）
 * <p>
 * 用户登出 / token 过期 → 抛异常 → WebhookFailureClassifier 映射为 TRANSIENT_DEPENDENCY → 重试 1/5/15min → 死信（用户已确认接受）。
 */
@Component
public class WebhookCrmTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(WebhookCrmTokenResolver.class);

    private final CrmAuthService crmAuthService;
    private final OssUserTokenCache ossUserTokenCache;
    private final CrmUserTokenCache userTokenCache;
    private final UserProfileCache userProfileCache;

    public WebhookCrmTokenResolver(CrmAuthService crmAuthService,
                                   OssUserTokenCache ossUserTokenCache,
                                   CrmUserTokenCache userTokenCache,
                                   UserProfileCache userProfileCache) {
        this.crmAuthService = crmAuthService;
        this.ossUserTokenCache = ossUserTokenCache;
        this.userTokenCache = userTokenCache;
        this.userProfileCache = userProfileCache;
    }

    /**
     * 获取指定用户的 CRM JWT token，强制使用该用户的 OSS token。
     *
     * @param username 操作者 username（来自 webhook_delivery_tasks.operator_username）
     * @return 该用户的 CRM JWT token
     * @throws TokenUnavailableException 用户 OSS token 不可用或 generateToken 失败（映射为 TRANSIENT_DEPENDENCY 允许重试）
     */
    public String getValidTokenForUserStrict(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException("Cannot get strict user token: username is empty");
        }
        UserProfileCache.CachedUserProfile p = userProfileCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
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
                .orElseThrow(() -> new TokenUnavailableException(
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
            userProfileCache.invalidate(username);
            log.info("Webhook CRM JWT token cache cleared for user={} due to 401", username);
        }
    }
}

