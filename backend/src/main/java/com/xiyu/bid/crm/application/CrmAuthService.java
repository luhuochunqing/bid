package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * CRM 鉴权：spec 037 fallback 版。
 * <p>OSS token 存在时走 {@code postWithAuth}（原路径，测试环境 + 已登录用户）；
 * OSS token 缺失时 fallback 到 {@code postJson}（无 Authorization，生产环境 + 未登录用户）。
 * <p><b>环境行为矩阵</b>：
 * <ul>
 *   <li>生产 + 已登录（有 OSS token） → postWithAuth，正常</li>
 *   <li>生产 + 未登录（无 OSS token） → fallback postJson，治本（PM 未登录也能换 JWT）</li>
 *   <li>测试 + 已登录（有 OSS token） → postWithAuth，正常</li>
 *   <li>测试 + 未登录（无 OSS token） → fallback postJson，但测试环境 generateToken 要求 Authorization → 失败（CRM 配置问题，非代码问题）</li>
 * </ul>
 * <p>三步认证流程：
 * <ol>
 *   <li><b>OSS token</b>：用户登录 OSS 时拿到的 access_token（可选，缺失时 fallback）</li>
 *   <li><b>CRM JWT</b>：{@code POST /common/inner/generateToken}，
 *       OSS token 存在时 {@code postWithAuth}（带 Authorization），
 *       OSS token 缺失时 {@code postJson}（无 Authorization）</li>
 *   <li><b>业务接口</b>：Header {@code Authorization: Bearer <CRM JWT>}</li>
 * </ol>
 * <p><b>客户禁令</b>：禁止使用"系统服务号/全局账号"换取 CRM JWT（客户明确要求必须基于真实用户 OSS token）。
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
     * <p>spec 037 fallback：OSS token 存在时走 postWithAuth（原路径），
     * OSS token 缺失时 fallback 到 postJson（无 Authorization）。
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

    /**
     * spec 037 fallback：OSS token 存在时走 postWithAuth（原路径），
     * OSS token 缺失时 fallback 到 postJson（无 Authorization）。
     */
    private String fetchAndCacheUserToken(UserProfileCache.CachedUserProfile profile, String username) {
        String salesNo = StringUtils.hasText(profile.crmSalesNo()) ? profile.crmSalesNo() : username;
        String nickName = StringUtils.hasText(profile.fullName()) ? profile.fullName() : username;
        String crmJwt = ossUserTokenCache.get(username)
                .map(ossToken -> applyCrmTokenWithOssToken(ossToken, nickName, salesNo))
                .orElseGet(() -> {
                    log.info("OSS token missing for username={}, fallback to postJson (no Authorization)", username);
                    return applyCrmToken(nickName, salesNo);
                });
        userTokenCache.put(username, crmJwt, JwtTtlResolver.resolveTtlSeconds(crmJwt));
        return crmJwt;
    }

    /**
     * 用 OSS token 调 {@code /common/inner/generateToken} 换 CRM JWT（原路径，带 Authorization）。
     * <p>OSS token 存在时的首选路径，测试环境 + 已登录用户走这里。
     * <p>package-private 供 webhook 复用。
     */
    String applyCrmTokenWithOssToken(String ossAccessToken, String nickName, String salesNo) {
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.debug("CRM generateToken (with auth): nickName={}, salesNo={}", nickName, salesNo);
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

    /**
     * 用 nickName + salesNo 调 {@code /common/inner/generateToken} 换 CRM JWT（fallback，无 Authorization）。
     * <p>OSS token 缺失时的 fallback 路径，生产环境 + 未登录用户走这里（治本）。
     * <p>2026-07-16 实测：生产环境 generateToken 接口不校验 Authorization header，
     * 仅传 {@code nickName + salesNo} 即可换 CRM JWT。测试环境要求 Authorization，
     * 因此测试环境 + 未登录用户仍会失败（CRM 配置问题，非代码问题）。
     * <p>package-private 供 webhook 复用。
     */
    String applyCrmToken(String nickName, String salesNo) {
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.debug("CRM generateToken (fallback, no auth): nickName={}, salesNo={}", nickName, salesNo);
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
