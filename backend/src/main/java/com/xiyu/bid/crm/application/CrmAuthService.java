package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.config.CrmProperties;
import com.xiyu.bid.crm.domain.CrmToken;
import com.xiyu.bid.crm.domain.CrmTokenCache;
import com.xiyu.bid.crm.infrastructure.CrmHttpClient;
import com.xiyu.bid.crm.infrastructure.CrmResponseHandler;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;

import java.time.Instant;
import java.util.Optional;

/**
 * CRM 认证服务。
 * <p>支持双 token 体系：
 * <ol>
 *   <li><b>OSS token</b> — 从 base-oss-test 获取，用于组织架构等 OSS 接口（全局共享）</li>
 *   <li><b>CRM JWT token</b> — 通过 generateToken 接口用 OSS token 换取，用于商机/客户/消息等接口</li>
 * </ol>
 * <p>自 CO-152 起，CRM JWT token 支持按用户维度隔离：
 * 配置了 {@code crm_sales_no} 的用户使用专属 token，未配置的用户回退到全局共享 token（兼容存量行为）。
 */
@Service
public class CrmAuthService {

    private static final Logger log = LoggerFactory.getLogger(CrmAuthService.class);

    private final CrmHttpClient httpClient;
    private final CrmProperties properties;
    private final OssPermissionCache permissionCache;
    private final CrmUserTokenCache userTokenCache;
    private final UserRepository userRepository;

    /** OSS token 缓存（用于 OSS 组织架构接口） */
    private final CrmTokenCache ossTokenCache = new CrmTokenCache();
    /** CRM JWT token 缓存（用于商机/客户/消息接口，全局共享，fallback） */
    private final CrmTokenCache crmTokenCache = new CrmTokenCache();

    private volatile int consecutiveFailures = 0;
    private volatile Instant coolDownUntil = null;

    public CrmAuthService(CrmHttpClient httpClient, CrmProperties properties,
                          OssPermissionCache permissionCache,
                          CrmUserTokenCache userTokenCache, UserRepository userRepository) {
        this.httpClient = httpClient;
        this.properties = properties;
        this.permissionCache = permissionCache;
        this.userTokenCache = userTokenCache;
        this.userRepository = userRepository;
    }

    /**
     * 获取 OSS token（用于 base-oss-test 组织架构接口）。
     */
    public String getValidOssToken() {
        return ossTokenCache.getOrFetch(this::applyOssToken, properties.getTokenRenewBeforeExpiryRatio())
                .accessToken();
    }

    /**
     * 获取 CRM JWT token（用于商机/客户/消息等接口）。
     * 内部自动先获取 OSS token，再调用 generateToken 转换。
     */
    public String getValidToken() {
        return crmTokenCache.getOrFetch(this::applyCrmToken, properties.getTokenRenewBeforeExpiryRatio())
                .accessToken();
    }

    public void logout() {
        CrmToken token = ossTokenCache.get().orElse(null);
        if (token != null) {
            try {
                String baseUrl = properties.getEffectiveAuthBaseUrl();
                String path = properties.getAuth().getLogoutPath();
                LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
                httpClient.postForm(baseUrl, path, formData, token.accessToken());
                log.info("OSS logout called: baseUrl={}, path={}", baseUrl, path);
            } catch (RuntimeException e) {
                log.warn("OSS logout request failed (non-fatal): {}", e.getMessage());
            }
        }
        ossTokenCache.clear();
        crmTokenCache.clear();
        permissionCache.clear();
        log.info("CRM token caches cleared (logout)");
    }

    public void logoutByToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return;
        }
        try {
            String baseUrl = properties.getEffectiveAuthBaseUrl();
            String path = properties.getAuth().getLogoutPath();
            LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            httpClient.postForm(baseUrl, path, formData, accessToken);
            log.info("OSS logout by token: baseUrl={}, path={}", baseUrl, path);
        } catch (RuntimeException e) {
            log.warn("OSS logout by token failed (non-fatal): {}", e.getMessage());
        }
        ossTokenCache.clear();
        crmTokenCache.clear();
        permissionCache.clear();
    }

    public void handleUnauthorized() {
        crmTokenCache.clear();
        log.info("CRM JWT token cache cleared due to 401, will re-apply on next request");
    }

    // ===== CO-152: 按用户维度 CRM token 管理 =====

    /**
     * 获取当前用户的 CRM JWT token（CO-152）。
     * 配置了 crm_sales_no → 专属 token（按 username 缓存）；否则回退到全局共享 token。
     */
    public String getValidTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            return getValidToken();
        }
        // CO-152 Review D1-2: 拆为命名私有方法，避免 Optional 嵌套 lambda 副作用
        return userRepository.findByUsername(username)
                .filter(u -> u.getCrmSalesNo() != null && !u.getCrmSalesNo().isBlank())
                .map(user -> resolveUserTokenFromCacheOrFetch(user, username))
                .orElseGet(() -> fallbackSharedToken(username));
    }

    private String resolveUserTokenFromCacheOrFetch(User user, String username) {
        return userTokenCache.get(username).orElseGet(() -> fetchAndCacheUserToken(user, username));
    }

    private String fetchAndCacheUserToken(User user, String username) {
        String token = applyCrmTokenForUser(user.getFullName(), user.getCrmSalesNo());
        userTokenCache.put(username, token, 86400L);
        return token;
    }

    private String fallbackSharedToken(String username) {
        log.debug("User {} has no crm_sales_no or not found, falling back to shared token", username);
        return getValidToken();
    }

    /**
     * 用户 CRM 接口 401 时，清除该用户的 token 缓存（CO-152）。
     * <p>只清当前用户缓存，不影响其他用户。
     */
    public void handleUnauthorizedForUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            log.info("CRM JWT token cache cleared for user={} due to 401", username);
        }
    }

    /**
     * 主动使指定用户的 CRM token 缓存失效（CO-152）。
     * <p>用于 crmSalesNo 变更等主动失效场景。登出时通常不调用（让 TTL 自然过期）。
     */
    public void logoutUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            log.info("CRM JWT token cache invalidated for user={} (active invalidation)", username);
        }
    }

    /**
     * 用指定用户的 nickName + salesNo 调 generateToken 获取专属 CRM JWT token。
     */
    private String applyCrmTokenForUser(String nickName, String salesNo) {
        CrmToken ossToken;
        try {
            ossToken = ossTokenCache.getOrFetch(this::applyOssToken,
                    properties.getTokenRenewBeforeExpiryRatio());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot acquire CRM token: OSS token acquisition failed", e);
        }
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        log.info("CRM generateToken for user: baseUrl={}, path={}, nickName={}, salesNo={}",
                baseUrl, path, nickName, salesNo);
        String body = String.format(
                "{\"nickName\":\"%s\",\"salesNo\":\"%s\"}",
                escapeJson(nickName), escapeJson(salesNo));
        CrmResponseHandler.CrmApiResponse response = httpClient.postWithAuth(
                baseUrl, path, ossToken.accessToken(), body);
        if (response.success() && response.data() != null && response.data().isTextual()) {
            log.info("CRM JWT token acquired for salesNo={}", salesNo);
            return response.data().asText();
        }
        throw new IllegalStateException(
                "CRM generateToken failed for user: code=" + response.code() + " msg=" + response.msg());
    }

    /**
     * 步骤1：从 OSS 获取 OAuth token。
     */
    private CrmToken applyOssToken() {
        if (coolDownUntil != null && Instant.now().isBefore(coolDownUntil)) {
            throw new IllegalStateException(
                    "OSS token apply in cooldown after " + consecutiveFailures + " consecutive failures");
        }

        String baseUrl = properties.getEffectiveAuthBaseUrl();
        String path = properties.getAuth().getOauthLoginPath();
        LinkedMultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("username", properties.getOauthUsername());
        formData.add("password", properties.getOauthPassword());
        formData.add("system", properties.getAuth().getUserLoginSystem());
        log.info("OSS oauth login: baseUrl={}, path={}, username={}", baseUrl, path, properties.getOauthUsername());
        CrmResponseHandler.CrmApiResponse response = httpClient.postForm(baseUrl, path, formData);

        if (response.data() != null && response.data().has("access_token")) {
            String accessToken = response.data().path("access_token").asText();
            long expiresIn = response.data().path("expires_in").asLong(5998);
            consecutiveFailures = 0;
            coolDownUntil = null;
            CrmToken token = new CrmToken(accessToken, expiresIn, Instant.now());
            log.info("OSS token acquired: expiresIn={}s", expiresIn);
            return token;
        }

        consecutiveFailures++;
        if (consecutiveFailures >= properties.getTokenCoolDownRetries()) {
            coolDownUntil = Instant.now().plusMillis(properties.getTokenCoolDownMs());
            log.error("OSS token apply failed {} times consecutively, entering cooldown until {}",
                    consecutiveFailures, coolDownUntil);
        }
        throw new IllegalStateException(
                "OSS applyToken failed: code=" + response.code() + " msg=" + response.msg());
    }

    /**
     * 步骤2：用 OSS token 调用 generateToken 换取 CRM JWT token。
     */
    private CrmToken applyCrmToken() {
        // 先确保有 OSS token
        CrmToken ossToken;
        try {
            ossToken = ossTokenCache.getOrFetch(this::applyOssToken,
                    properties.getTokenRenewBeforeExpiryRatio());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Cannot acquire CRM token: OSS token acquisition failed", e);
        }

        String ossAccessToken = ossToken.accessToken();
        String baseUrl = properties.getEffectiveChanceBaseUrl();
        String path = properties.getAuth().getGenerateTokenPath();
        String nickName = properties.getGenerateTokenNickName();
        String salesNo = properties.getGenerateTokenSalesNo();

        log.info("CRM generateToken: baseUrl={}, path={}, nickName={}, salesNo={}",
                baseUrl, path, nickName, salesNo);

        String body = String.format(
                "{\"nickName\":\"%s\",\"salesNo\":\"%s\"}",
                escapeJson(nickName), escapeJson(salesNo));
        CrmResponseHandler.CrmApiResponse response = httpClient.postWithAuth(
                baseUrl, path, ossAccessToken, body);

        if (response.success() && response.data() != null && response.data().isTextual()) {
            String crmToken = response.data().asText();
            // CRM JWT 有效期通常较长（24h），但如果 generateToken 返回里没有 expires_in，默认 24h
            long expiresIn = 86400L;
            consecutiveFailures = 0;
            coolDownUntil = null;
            CrmToken token = new CrmToken(crmToken, expiresIn, Instant.now());
            log.info("CRM JWT token acquired: expiresIn={}s", expiresIn);
            return token;
        }

        consecutiveFailures++;
        if (consecutiveFailures >= properties.getTokenCoolDownRetries()) {
            coolDownUntil = Instant.now().plusMillis(properties.getTokenCoolDownMs());
            log.error("CRM JWT token apply failed {} times consecutively, entering cooldown until {}",
                    consecutiveFailures, coolDownUntil);
        }
        throw new IllegalStateException(
                "CRM generateToken failed: code=" + response.code() + " msg=" + response.msg());
    }

    private static String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
