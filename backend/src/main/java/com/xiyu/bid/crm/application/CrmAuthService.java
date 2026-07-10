package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * CRM 认证服务（CO-152：删除全局 03595 happy path）。
 * <p>
 * 严格双 token 体系，按用户身份：
 * <ol>
 *   <li>用户 OSS token（{@link OssUserTokenCache}）—— 登录时缓存</li>
 *   <li>用用户 OSS token 调 {@code generateToken} 换 CRM JWT（{@link CrmUserTokenCache}）</li>
 *   <li>业务接口使用 CRM JWT</li>
 * </ol>
 * <p>
 * 不再提供 {@code getValidToken()} / {@code getValidOssToken()} / 全局账号登录。
 * username 为空或用户 OSS 不可用时抛 {@link TokenUnavailableException}，由调用方降级。
 */
@Service
public class CrmAuthService {

    private static final Logger log = LoggerFactory.getLogger(CrmAuthService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final CrmUserTokenCache userTokenCache;
    private final OssUserTokenCache ossUserTokenCache;
    private final UserProfileCache userProfileCache;

    public CrmAuthService(CrmHttpClient httpClient, CrmProperties properties,
                          CrmUserTokenCache userTokenCache, OssUserTokenCache ossUserTokenCache,
                          UserProfileCache userProfileCache) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.userTokenCache = userTokenCache;
        this.ossUserTokenCache = ossUserTokenCache;
        this.userProfileCache = userProfileCache;
    }

    /**
     * 获取用户 CRM JWT（严格按用户身份，不回退全局 03595）。
     *
     * @param username 当前操作用户名（不可为空）
     * @return 该用户的 CRM JWT
     * @throws TokenUnavailableException 无用户 / 无 OSS token / generateToken 失败
     */
    public String getValidTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException("Cannot get user CRM token: username is empty");
        }
        UserProfileCache.CachedUserProfile profile = userProfileCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get user CRM token: user not found, username=" + username));
        return userTokenCache.get(username)
                .orElseGet(() -> fetchAndCacheUserToken(profile, username));
    }

    /**
     * 获取用户 OSS token（组织架构 / 菜单等直连 OSS 的接口）。
     *
     * @param username 当前操作用户名（不可为空）
     * @throws TokenUnavailableException 未登录或 token 已过期
     */
    public String getValidOssTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException("Cannot get user OSS token: username is empty");
        }
        return ossUserTokenCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get user OSS token: not found or expired, username=" + username
                                + " (user may have logged out or token expired)"));
    }

    /** 用户 CRM 接口 401：只清该用户 JWT + profile 缓存。 */
    public void handleUnauthorizedForUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.invalidate(username);
            log.info("CRM JWT token cache cleared for user={} due to 401", username);
        }
    }

    /**
     * 主动失效用户 CRM JWT 缓存（crmSalesNo 变更等）。
     * <p>不调 OSS logout；OSS token 由 {@code AuthService.logout} 清 {@link OssUserTokenCache}。
     */
    public void logoutUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.invalidate(username);
            log.info("CRM JWT token cache invalidated for user={} (active invalidation)", username);
        }
    }

    private String fetchAndCacheUserToken(UserProfileCache.CachedUserProfile profile, String username) {
        String salesNo = (profile.crmSalesNo() != null && !profile.crmSalesNo().isBlank())
                ? profile.crmSalesNo() : username;
        String nickName = (profile.fullName() != null && !profile.fullName().isBlank())
                ? profile.fullName() : username;
        String ossToken = ossUserTokenCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot acquire CRM token: user OSS token not found, username=" + username
                                + " (user may have logged out or token expired)"));
        String token = applyCrmTokenWithOssToken(ossToken, nickName, salesNo);
        long ttlSeconds = JwtTtlResolver.resolveTtlSeconds(token);
        userTokenCache.put(username, token, ttlSeconds);
        return token;
    }

    /**
     * 用指定 OSS token 调 generateToken 换 CRM JWT。
     * <p>package-private，供 {@link WebhookCrmTokenResolver} 使用。
     */
    String applyCrmTokenWithOssToken(String ossAccessToken, String nickName, String salesNo) {
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.info("CRM generateToken for user: baseUrl={}, path={}, nickName={}, salesNo={}",
                baseUrl, path, nickName, salesNo);
        String body = String.format(
                "{\"nickName\":\"%s\",\"salesNo\":\"%s\"}",
                CrmJsonUtils.escapeJson(nickName), CrmJsonUtils.escapeJson(salesNo));
        CrmResponseHandler.CrmApiResponse response = httpClient.postWithAuth(
                baseUrl, path, ossAccessToken, body);
        if (response.success() && response.data() != null && response.data().isTextual()) {
            log.info("CRM JWT token acquired for salesNo={}", salesNo);
            return response.data().asText();
        }
        throw new TokenUnavailableException(
                "CRM generateToken failed for user: code=" + response.code() + " msg=" + response.msg());
    }
}
