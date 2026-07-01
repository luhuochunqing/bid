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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * CRM 认证服务。支持双 token 体系：
 * - OSS token（全局共享，用于组织架构接口）
 * - CRM JWT token（CO-152 起按用户隔离：配置 crm_sales_no 的用户用专属 token，否则回退共享）
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

    // CO-152 Review D4-1: 用户 profile 缓存（username → fullName/crmSalesNo），避免每次 CRM 接口调用都查 DB
    private final ConcurrentMap<String, CachedUserProfile> userProfileCache = new ConcurrentHashMap<>();
    private record CachedUserProfile(String fullName, String crmSalesNo, Instant expiresAt) {}
    private static final long USER_PROFILE_CACHE_TTL_SECONDS = 300; // 5 分钟

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
     * crm_sales_no 有值 → 用 crmSalesNo；否则用 username 作为 salesNo（OSS 用户名即工号）。
     */
    public String getValidTokenForUser(String username) {
        if (username == null || username.isBlank()) {
            return getValidToken();
        }
        CachedUserProfile p = getCachedUserProfile(username).orElse(null);
        if (p == null) {
            return getValidToken();
        }
        String salesNo = (p.crmSalesNo() != null && !p.crmSalesNo().isBlank()) ? p.crmSalesNo() : username;
        if (salesNo.equals(username) && p.crmSalesNo() == null) {
            String nickName = (p.fullName() != null && !p.fullName().isBlank()) ? p.fullName() : username;
            p = new CachedUserProfile(nickName, salesNo, Instant.now().plusSeconds(USER_PROFILE_CACHE_TTL_SECONDS));
            userProfileCache.put(username, p);
        }
        return resolveUserTokenFromCacheOrFetch(p, username);
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

    private String resolveUserTokenFromCacheOrFetch(CachedUserProfile profile, String username) {
        return userTokenCache.get(username).orElseGet(() -> fetchAndCacheUserToken(profile, username));
    }

    private String fetchAndCacheUserToken(CachedUserProfile profile, String username) {
        String token = applyCrmTokenForUser(profile.fullName(), profile.crmSalesNo());
        userTokenCache.put(username, token, 86400L);
        return token;
    }

    /** 用户 CRM 接口 401 时清除该用户 token 缓存（只清当前用户，不影响其他用户）。 */
    public void handleUnauthorizedForUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.remove(username); // D4-1: 同步清 profile 缓存
            log.info("CRM JWT token cache cleared for user={} due to 401", username);
        }
    }

    /** 主动使指定用户的 CRM token 缓存失效（用于 crmSalesNo 变更等场景；登出不调用）。 */
    public void logoutUser(String username) {
        if (username != null && !username.isBlank()) {
            userTokenCache.invalidate(username);
            userProfileCache.remove(username); // D4-1: 同步清 profile 缓存
            log.info("CRM JWT token cache invalidated for user={} (active invalidation)", username);
        }
    }

    /** 用指定用户的 nickName + salesNo 调 generateToken 获取专属 CRM JWT token。 */
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

    /** 步骤1：从 OSS 获取 OAuth token。 */
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

    /** 步骤2：用 OSS token 调用 generateToken 换取 CRM JWT token。 */
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
