package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.domain.CrmToken;
import com.xiyu.bid.crm.domain.CrmTokenCache;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.StringUtils;

/**
 * CRM 认证（CO-152 闭环）：两条<strong>显式</strong>身份，禁止 silent 混用。
 * <ul>
 *   <li><b>用户路径</b>：登录缓存的用户 OSS → generateToken(用户 nick/salesNo) → CRM JWT</li>
 *   <li><b>系统集成账号路径</b>：配置的后台专用 OSS 账号 → generateToken(系统 nick/salesNo) → CRM JWT
 *       （用于自动分配、外部推送反查、无 operator 的 webhook 等无用户上下文流量）</li>
 * </ul>
 * 配置项 {@code app.crm.oauth-username/password} + {@code generate-token-*} 表示
 * <strong>系统集成账号</strong>（须为可生产登录的专用服务身份，禁止再用个人号当暗门）。
 */
@Service
public class CrmAuthService {

    private static final Logger log = LoggerFactory.getLogger(CrmAuthService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final CrmUserTokenCache userTokenCache;
    private final OssUserTokenCache ossUserTokenCache;
    private final UserProfileCache userProfileCache;

    /** 系统集成账号：OSS token 缓存 */
    private final CrmTokenCache systemOssTokenCache = new CrmTokenCache();
    /** 系统集成账号：CRM JWT 缓存 */
    private final CrmTokenCache systemCrmTokenCache = new CrmTokenCache();

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
     * 调用方路由：username 非空 → 用户路径；为空 → <strong>显式</strong>系统集成账号（非 03595 暗门回退）。
     */
    public String getValidTokenForCaller(String username) {
        if (username == null || username.isBlank()) {
            return getValidTokenForSystem();
        }
        return getValidTokenForUser(username);
    }

    /** 用户 CRM JWT（必须有用户 OSS 缓存）。 */
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

    /** 用户 OSS token（菜单等直连 OSS）。 */
    public String getValidOssTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException("Cannot get user OSS token: username is empty");
        }
        return ossUserTokenCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get user OSS token: not found or expired, username=" + username));
    }

    /**
     * 系统集成账号 CRM JWT（后台无用户上下文专用）。
     * <p>三步：配置账号登录 OSS → generateToken(系统 nick/salesNo) → JWT。
     */
    public String getValidTokenForSystem() {
        ensureSystemAccountConfigured();
        return systemCrmTokenCache.getOrFetch(this::applySystemCrmToken,
                properties.getTokenRenewBeforeExpiryRatio()).accessToken();
    }

    /** 用户 401：联合清理 CRM JWT + profile + <strong>底层 OSS</strong>（堵住坏钥匙死循环）。 */
    public void handleUnauthorizedForUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        userTokenCache.invalidate(username);
        userProfileCache.invalidate(username);
        ossUserTokenCache.invalidate(username);
        log.info("User CRM JWT + profile + OSS token cache cleared for user={} due to 401", username);
    }

    /** 系统集成账号 401：清系统 OSS + CRM JWT 缓存。 */
    public void handleUnauthorizedForSystem() {
        systemCrmTokenCache.clear();
        systemOssTokenCache.clear();
        log.info("System integration CRM/OSS token caches cleared due to 401");
    }

    /** 按调用方路由 401 清理。 */
    public void handleUnauthorizedForCaller(String username) {
        if (username == null || username.isBlank()) {
            handleUnauthorizedForSystem();
        } else {
            handleUnauthorizedForUser(username);
        }
    }

    public void logoutUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.invalidate(username);
            // 登出时 OSS 由 AuthService 清；此处仅清 CRM JWT
            log.info("CRM JWT token cache invalidated for user={}", username);
        }
    }

    private String fetchAndCacheUserToken(UserProfileCache.CachedUserProfile profile, String username) {
        String salesNo = StringUtils.hasText(profile.crmSalesNo()) ? profile.crmSalesNo() : username;
        String nickName = StringUtils.hasText(profile.fullName()) ? profile.fullName() : username;
        String ossToken = ossUserTokenCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot acquire CRM token: user OSS token not found, username=" + username));
        String token = applyCrmTokenWithOssToken(ossToken, nickName, salesNo);
        userTokenCache.put(username, token, JwtTtlResolver.resolveTtlSeconds(token));
        return token;
    }

    /** package-private：Webhook / 单测可复用 generateToken 调用。 */
    String applyCrmTokenWithOssToken(String ossAccessToken, String nickName, String salesNo) {
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.debug("CRM generateToken: nickName={}, salesNo={}", nickName, salesNo);
        String body = String.format("{\"nickName\":\"%s\",\"salesNo\":\"%s\"}",
                CrmJsonUtils.escapeJson(nickName), CrmJsonUtils.escapeJson(salesNo));
        CrmResponseHandler.CrmApiResponse response = httpClient.postWithAuth(
                baseUrl, path, ossAccessToken, body);
        if (response.success() && response.data() != null && response.data().isTextual()) {
            return response.data().asText();
        }
        throw new TokenUnavailableException(
                "CRM generateToken failed: code=" + response.code() + " msg=" + response.msg());
    }

    private CrmToken applySystemCrmToken() {
        CrmToken oss = systemOssTokenCache.getOrFetch(this::applySystemOssToken,
                properties.getTokenRenewBeforeExpiryRatio());
        String jwt = applyCrmTokenWithOssToken(oss.accessToken(),
                properties.getGenerateTokenNickName(), properties.getGenerateTokenSalesNo());
        return new CrmToken(jwt, JwtTtlResolver.resolveTtlSeconds(jwt), java.time.Instant.now());
    }

    private CrmToken applySystemOssToken() {
        ensureSystemAccountConfigured();
        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getOauthLoginPath();
        LinkedMultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", properties.getOauthUsername());
        form.add("password", properties.getOauthPassword());
        form.add("system", properties.getAuth().getUserLoginSystem());
        log.info("System integration OSS oauth login: baseUrl={}, path={}, username={}",
                baseUrl, path, properties.getOauthUsername());
        CrmResponseHandler.CrmApiResponse response = httpClient.postForm(baseUrl, path, form);
        if (response.data() != null && response.data().has("access_token")) {
            String accessToken = response.data().path("access_token").asText();
            long expiresIn = response.data().path("expires_in").asLong(5998);
            return new CrmToken(accessToken, expiresIn, java.time.Instant.now());
        }
        throw new TokenUnavailableException(
                "System integration OSS login failed: code=" + response.code() + " msg=" + response.msg()
                        + " (configure XIYU_CRM_OAUTH_USERNAME/PASSWORD as dedicated service account)");
    }

    private void ensureSystemAccountConfigured() {
        if (!StringUtils.hasText(properties.getOauthUsername())
                || !StringUtils.hasText(properties.getOauthPassword())) {
            throw new TokenUnavailableException(
                    "System integration account not configured "
                            + "(XIYU_CRM_OAUTH_USERNAME/PASSWORD required for background CRM calls)");
        }
        if (!StringUtils.hasText(properties.getGenerateTokenNickName())
                || !StringUtils.hasText(properties.getGenerateTokenSalesNo())) {
            throw new TokenUnavailableException(
                    "System generateToken identity not configured "
                            + "(XIYU_CRM_GENERATE_TOKEN_NICK_NAME/SALES_NO required)");
        }
    }
}
