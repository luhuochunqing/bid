package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CRM 鉴权：spec 037 简化为两步（去掉 OSS token 依赖）。
 * <p>2026-07-16 实测：生产环境 generateToken 接口不校验 Authorization header，
 * 仅传 {@code nickName + salesNo} 即可换 CRM JWT。因此去掉步骤 1（OSS token），
 * 直接用 nickName + salesNo 调 generateToken。
 * <p>原三步流程（保留注释供回退参考）：
 * <ol>
 *   <li><b>OSS token</b>（已弃用）：用户登录 OSS 时拿到的 access_token</li>
 *   <li><b>CRM JWT</b>：{@code POST /common/inner/generateToken}，
 *       spec 037 前 Header {@code Authorization: Bearer <OSS token>}，
 *       spec 037 后改用 {@link CrmHttpClient#postJson}（无 Authorization）</li>
 *   <li><b>业务接口</b>：Header {@code Authorization: Bearer <CRM JWT>}</li>
 * </ol>
 * <p>
 * <b>回退路径</b>：若客户方修复 generateToken 不校验 Authorization 的"漏洞"，
 * 把 {@link #applyCrmToken} 方法体改回 {@code httpClient.postWithAuth(baseUrl, path, ossToken, body)}
 * 即可恢复三步认证（{@link OssUserTokenCache} 仍保留注入）。
 * <p>
 * <b>客户禁令</b>：禁止使用"系统服务号/全局账号"换取 CRM JWT（客户明确要求必须基于真实用户）。
 * 无用户、无用户 profile 时抛 {@link TokenUnavailableException}。
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
     * 获取用户 CRM JWT。
     * <p>spec 037：不再要求用户已登录（OSS token 不再必需），
     * 仅需用户 profile（fullName + crmSalesNo）即可换 JWT。
     * <p>username 为空、用户不存在 → {@link TokenUnavailableException}。
     */
    public String getValidTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException(
                    "Cannot get CRM token: username is empty");
        }
        UserProfileCache.CachedUserProfile profile = userProfileCache.get(username)
                .orElseThrow(() -> new TokenUnavailableException(
                        "Cannot get CRM token: user not found, username=" + username));
        return userTokenCache.get(username)
                .orElseGet(() -> fetchAndCacheUserToken(profile, username));
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

    public void logoutUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.invalidate(username);
            log.info("CRM JWT cache invalidated for user={}", username);
        }
    }

    private String fetchAndCacheUserToken(UserProfileCache.CachedUserProfile profile, String username) {
        // spec 037：去掉 OSS token 依赖，直接用 nickName + salesNo 换 CRM JWT
        String salesNo = StringUtils.hasText(profile.crmSalesNo()) ? profile.crmSalesNo() : username;
        String nickName = StringUtils.hasText(profile.fullName()) ? profile.fullName() : username;
        String crmJwt = applyCrmToken(nickName, salesNo);
        userTokenCache.put(username, crmJwt, JwtTtlResolver.resolveTtlSeconds(crmJwt));
        return crmJwt;
    }

    /**
     * 用 nickName + salesNo 调 {@code /common/inner/generateToken} 换 CRM JWT。
     * <p>spec 037：改用 {@link CrmHttpClient#postJson}（无 Authorization），
     * 因为生产环境实测 generateToken 不校验 Authorization。
     * <p>package-private 供 webhook 复用。
     */
    String applyCrmToken(String nickName, String salesNo) {
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.debug("CRM generateToken: nickName={}, salesNo={}", nickName, salesNo);
        String body = String.format("{\"nickName\":\"%s\",\"salesNo\":\"%s\"}",
                CrmJsonUtils.escapeJson(nickName), CrmJsonUtils.escapeJson(salesNo));
        CrmResponseHandler.CrmApiResponse response = httpClient.postJson(baseUrl, path, body);
        if (response.success() && response.data() != null && response.data().isTextual()) {
            return response.data().asText();
        }
        throw new TokenUnavailableException(
                "CRM generateToken failed: code=" + response.code() + " msg=" + response.msg());
    }
}
