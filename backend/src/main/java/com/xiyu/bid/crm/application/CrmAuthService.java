package com.xiyu.bid.crm.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;

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
 * <p><b>spec 037 设计 Review 修正</b>：
 * <ul>
 *   <li>H2：JSON body 改用 {@link ObjectMapper} 序列化，替代手工 {@code String.format} 拼装，
 *       覆盖 JSON 规范要求的全部控制字符转义（原 CrmJsonUtils.escapeJson 漏处理 \b \f 等）</li>
 *   <li>M4：{@link #handleUnauthorizedForUser} 不再清 OSS cache —— CRM JWT 401 与 OSS token 独立，
 *       误清 OSS 会让"菜单直连 OSS 接口"在 CRM 401 后需要重新登录。OSS token 失效应由 OSS 接口自身 401 触发</li>
 * </ul>
 */
@Service
public class CrmAuthService {

    private static final Logger log = LoggerFactory.getLogger(CrmAuthService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
     * 用户 CRM 接口 401：清理 CRM JWT + profile，触发下次取 token 时重新 generateToken。
     * <p>spec 037 Review M4：不再清 OSS cache —— generateToken fallback 路径不依赖 OSS token，
     * 且 CRM JWT 401 与 OSS token 独立。误清 OSS 会让"菜单直连 OSS 接口"在 CRM 401 后需要重新登录。
     * OSS token 失效应由 OSS 接口自身 401 触发，或由 {@code AuthService.logout} 主动清。
     */
    public void handleUnauthorizedForUser(String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        userTokenCache.invalidate(username);
        userProfileCache.invalidate(username);
        log.info("User CRM JWT + profile cleared for user={} due to 401 (OSS cache preserved)", username);
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
        var ossToken = ossUserTokenCache.get(username);
        if (ossToken.isEmpty()) {
            log.info("OSS token missing for username={}, fallback to postJson (no Authorization)", username);
        }
        String crmJwt = fetchCrmToken(nickName, salesNo, ossToken);
        userTokenCache.put(username, crmJwt, JwtTtlResolver.resolveTtlSeconds(crmJwt));
        return crmJwt;
    }

    /**
     * 调 {@code /common/inner/generateToken} 换 CRM JWT。
     * <p>spec 037 Review 3.1：合并原 {@code applyCrmTokenWithOssToken}（postWithAuth）
     * 与 {@code applyCrmToken}（postJson）两个方法 —— 它们仅 HTTP 调用方式不同，
     * body 构造、响应解析、异常抛出完全一致，合并后消除 12 行重复代码。
     * <p>OSS token 存在时走 {@code postWithAuth}（带 Authorization，测试环境 + 已登录用户）；
     * OSS token 缺失时 fallback 到 {@code postJson}（无 Authorization，生产环境 + 未登录用户）。
     * <p>2026-07-16 实测：生产环境 generateToken 接口不校验 Authorization header，
     * 仅传 {@code nickName + salesNo} 即可换 CRM JWT。测试环境要求 Authorization，
     * 因此测试环境 + 未登录用户仍会失败（CRM 配置问题，非代码问题）。
     */
    private String fetchCrmToken(String nickName, String salesNo, Optional<String> ossToken) {
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.debug("CRM generateToken: nickName={}, salesNo={}, withAuth={}", nickName, salesNo, ossToken.isPresent());
        String body = buildGenerateTokenBody(nickName, salesNo);
        CrmResponseHandler.CrmApiResponse response = ossToken
                .map(t -> httpClient.postWithAuth(baseUrl, path, t, body))
                .orElseGet(() -> httpClient.postJson(baseUrl, path, body));
        if (response.success() && response.data() != null && response.data().isTextual()) {
            return response.data().asText();
        }
        throw new TokenUnavailableException(
                "CRM generateToken failed: code=" + response.code() + " msg=" + response.msg());
    }

    /**
     * 构造 generateToken 请求 body（spec 037 Review H2：用 ObjectMapper 替代手工拼装）。
     * <p>原 {@code String.format + CrmJsonUtils.escapeJson} 漏处理 {@code \b \f} 等 JSON 规范控制字符，
     * 改用 ObjectMapper 保证完整覆盖 JSON 规范要求的转义。
     */
    private static String buildGenerateTokenBody(String nickName, String salesNo) {
        try {
            return MAPPER.writeValueAsString(Map.of("nickName", nickName, "salesNo", salesNo));
        } catch (JsonProcessingException e) {
            // nickName/salesNo 都是普通字符串，理论上不会序列化失败
            throw new IllegalStateException("Failed to serialize generateToken body", e);
        }
    }
}
