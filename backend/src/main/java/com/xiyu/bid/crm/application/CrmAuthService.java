package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CRM 鉴权：严格对齐接口文档三步（见 CRM generateToken 契约）。
 * <ol>
 *   <li><b>OSS token</b>：用户登录 OSS 时拿到的 access_token，缓存在 {@link OssUserTokenCache}</li>
 *   <li><b>CRM JWT</b>：{@code POST /common/inner/generateToken}，
 *       Header {@code Authorization: Bearer <OSS token>}，Body {@code nickName + salesNo}</li>
 *   <li><b>业务接口</b>：Header {@code Authorization: Bearer <CRM JWT>}</li>
 * </ol>
 * <p>
 * <b>没有</b>全局/系统服务号路径。无用户、无用户 OSS 缓存时抛 {@link TokenUnavailableException}，
 * 由调用方降级或失败——不假装有配置账号可登录。
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
     * 获取用户 CRM JWT（步骤 1+2）。
     * <p>username 为空、用户不存在、或用户未登录/OSS 已过期 → {@link TokenUnavailableException}。
     */
    public String getValidTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException(
                    "Cannot get CRM token: username is empty (need a logged-in user OSS token)");
        }
        UserProfileCache.CachedUserProfile profile = userProfileCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get CRM token: user not found, username=" + username));
        return userTokenCache.get(username)
                .orElseGet(() -> fetchAndCacheUserToken(profile, username));
    }

    /**
     * 兼容旧调用名：与 {@link #getValidTokenForUser(String)} 相同。
     * <p>username 为空时<strong>不再</strong>走任何配置账号，直接失败。
     */
    public String getValidTokenForCaller(String username) {
        return getValidTokenForUser(username);
    }

    /** 用户 OSS token（仅步骤 1，供菜单等直连 OSS 的接口）。 */
    public String getValidOssTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException("Cannot get OSS token: username is empty");
        }
        return ossUserTokenCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get OSS token: not found or expired, username=" + username
                                + " (user must login again)"));
    }

    /**
     * 用户 CRM 接口 401：联合清理 JWT + profile + <strong>OSS</strong>，
     * 避免「只清 JWT、仍用坏 OSS 反复 generateToken」。
     */
    public void handleUnauthorizedForUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        userTokenCache.invalidate(username);
        userProfileCache.invalidate(username);
        ossUserTokenCache.invalidate(username);
        log.info("User CRM JWT + profile + OSS cleared for user={} due to 401", username);
    }

    /** 与 {@link #handleUnauthorizedForUser(String)} 相同（无系统账号分支）。 */
    public void handleUnauthorizedForCaller(String username) {
        handleUnauthorizedForUser(username);
    }

    public void logoutUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.invalidate(username);
            log.info("CRM JWT cache invalidated for user={}", username);
        }
    }

    private String fetchAndCacheUserToken(UserProfileCache.CachedUserProfile profile, String username) {
        // 步骤 1：用户登录时缓存的 OSS token
        String ossToken = ossUserTokenCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get CRM token: user OSS token missing, username=" + username
                                + " (login first; no system account fallback)"));
        // 步骤 2：OSS → CRM JWT（文档 generateToken）
        String salesNo = StringUtils.hasText(profile.crmSalesNo()) ? profile.crmSalesNo() : username;
        String nickName = StringUtils.hasText(profile.fullName()) ? profile.fullName() : username;
        String crmJwt = applyCrmTokenWithOssToken(ossToken, nickName, salesNo);
        userTokenCache.put(username, crmJwt, JwtTtlResolver.resolveTtlSeconds(crmJwt));
        return crmJwt;
    }

    /**
     * 步骤 2：用 OSS token 调 {@code /common/inner/generateToken} 换 CRM JWT。
     * <p>package-private 供 webhook 复用。
     */
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
}
