package com.xiyu.bid.crm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Data
@Component
@ConfigurationProperties(prefix = "app.crm")
public class CrmProperties {

    /** CONNECT_TIMEOUT_MS. */
    private static final int CONNECT_TIMEOUT_MS = 5000;
    /** READ_TIMEOUT_MS. */
    private static final int READ_TIMEOUT_MS = 30000;
    /** DEFAULT_RETRIES. */
    private static final int DEFAULT_RETRIES = 3;
    /** BASE_DELAY_MS. */
    private static final int BASE_DELAY_MS = 1000;
    /** MAX_DELAY_MS. */
    private static final int MAX_DELAY_MS = 30000;
    /** TOKEN_RENEW_RATIO. */
    private static final int TOKEN_RENEW_RATIO = 10;
    /** COOL_DOWN_RETRIES. */
    private static final int COOL_DOWN_RETRIES = 3;
    /** COOL_DOWN_MS. */
    private static final long COOL_DOWN_MS = 60000;
    /** MENU_CACHE_TTL_SEC. */
    private static final int MENU_CACHE_TTL_SEC = 1800;
    /** MSG_BATCH_MAX. */
    private static final int MSG_BATCH_MAX = 100;
    /** MAX_CONNECTIONS. */
    private static final int MAX_CONNECTIONS = 50;

    /** CRM application client ID. */
    private String clientId;

    /** CRM application client secret. */
    private String clientSecret;

    /** Base URL for CRM API endpoints (backward compatible). */
    private String baseUrl;

    /** Base URL for auth/org/employee/menu APIs.
     * Falls back to baseUrl if not set. */
    private String authBaseUrl;

    /** Base URL for customer APIs. Falls back to baseUrl if not set. */
    private String customerBaseUrl;

    /** Base URL for message APIs. Falls back to baseUrl if not set. */
    private String messageBaseUrl;

    /** Base URL for chance/opportunity APIs.
     * Falls back to baseUrl if not set. */
    private String chanceBaseUrl;

    /** Base URL for contact person APIs. Falls back to baseUrl if not set. */
    private String contactPersonBaseUrl;

    /** Auth API path configuration. */
    private CrmAuthPaths auth = new CrmAuthPaths();

    /** Customer API path configuration. */
    private CrmCustomerPaths customer = new CrmCustomerPaths();

    /** Message API path configuration. */
    private CrmMessagePaths message = new CrmMessagePaths();

    /** Chance (opportunity) API path configuration. */
    private CrmChancePaths chance = new CrmChancePaths();

    /** Contact person API path configuration. */
    private CrmContactPersonPaths contactPerson = new CrmContactPersonPaths();

    /** HTTP connect timeout in milliseconds. */
    private int connectTimeoutMs = CONNECT_TIMEOUT_MS;

    /** HTTP read timeout in milliseconds. */
    private int readTimeoutMs = READ_TIMEOUT_MS;

    /** Maximum retry attempts for 5xx errors. */
    private int maxRetries = DEFAULT_RETRIES;

    /** Base retry delay for exponential backoff. */
    private int retryBaseDelayMs = BASE_DELAY_MS;

    /** Maximum retry delay in milliseconds. */
    private int retryMaxDelayMs = MAX_DELAY_MS;

    /** Ratio (percent) of TTL remaining before triggering auto-renewal. */
    private int tokenRenewBeforeExpiryRatio = TOKEN_RENEW_RATIO;

    /** Number of consecutive failures before entering cooldown. */
    private int tokenCoolDownRetries = COOL_DOWN_RETRIES;

    /** Cooldown duration in milliseconds. */
    private long tokenCoolDownMs = COOL_DOWN_MS;

    /** OAuth login username. */
    private String oauthUsername = "";

    /** OAuth login password. */
    private String oauthPassword = "";

    /** OAuth login system code. */
    private String oauthSystem = "HOME";

    /** GenerateToken path for CRM JWT token exchange. */
    private String generateTokenPath = "/common/inner/generateToken";

    /** CRM JWT token exchange: nickName (用户昵称). */
    private String generateTokenNickName = "";

    /** CRM JWT token exchange: salesNo (用户工号). */
    private String generateTokenSalesNo = "";


    /** Menu tree cache TTL in seconds. */
    private int menuCacheTtlSeconds = MENU_CACHE_TTL_SEC;

    /** Maximum messages per batch request. */
    private int messageBatchMaxSize = MSG_BATCH_MAX;

    /** Maximum HTTP connections in the pool. */
    private int maxConnections = MAX_CONNECTIONS;

    /**
     * Returns the effective auth base URL.
     * Uses authBaseUrl if set, otherwise falls back to baseUrl.
     * @return effective auth base URL
     */
    public String getEffectiveAuthBaseUrl() {
        return StringUtils.hasText(authBaseUrl) ? authBaseUrl : baseUrl;
    }

    /**
     * Returns the effective customer base URL.
     * Uses customerBaseUrl if set, otherwise falls back to baseUrl.
     * @return effective customer base URL
     */
    public String getEffectiveCustomerBaseUrl() {
        return StringUtils.hasText(customerBaseUrl) ? customerBaseUrl : baseUrl;
    }

    /**
     * Returns the effective message base URL.
     * Uses messageBaseUrl if set, otherwise falls back to baseUrl.
     * @return effective message base URL
     */
    public String getEffectiveMessageBaseUrl() {
        return StringUtils.hasText(messageBaseUrl) ? messageBaseUrl : baseUrl;
    }

    /**
     * Returns the effective chance base URL.
     * Uses chanceBaseUrl if set, otherwise falls back to baseUrl.
     * @return effective chance base URL
     */
    public String getEffectiveChanceBaseUrl() {
        return StringUtils.hasText(chanceBaseUrl) ? chanceBaseUrl : baseUrl;
    }

    /**
     * Returns the effective contact person base URL.
     * Uses contactPersonBaseUrl if set, otherwise falls back to baseUrl.
     * @return effective contact person base URL
     */
    public String getEffectiveContactPersonBaseUrl() {
        return StringUtils.hasText(contactPersonBaseUrl)
            ? contactPersonBaseUrl : baseUrl;
    }

    @Data
    public static class CrmAuthPaths {
        /** applyTokenPath (deprecated, use oauthLoginPath). */
        private String applyTokenPath = "/auth/applyToken";
        /** OAuth login path. */
        private String oauthLoginPath = "/oauth/login";
        /** generateTokenPath. */
        private String generateTokenPath = "/common/inner/generateToken";

        /** logoutPath. */
        private String logoutPath = "/oauth/logout";
        /** menuTreePath. */
        private String menuTreePath = "/menu/tree";
        /** employeePath. */
        private String employeePath = "/oauth/getUserInfo";
        /** userPermissionPath - GET /oauth/getUserPermission. */
        private String userPermissionPath = "/oauth/getUserPermission";
        /** userPermissionSystemName - default query param for getUserPermission. */
        private String userPermissionSystemName = "xiyu-bid-poc";
    }

    @Data
    public static class CrmCustomerPaths {
        /** searchPath. */
        private String searchPath = "/customer/search";
        /** contactsPath. */
        private String contactsPath = "/customer/contacts/batch";
    }

    @Data
    public static class CrmChancePaths {
        /** pageListPath. */
        private String pageListPath = "/customer-chance/page-list";
        /** detailPath（按商机主键 id 查商机详情，返回含 code 商机编号）. */
        private String detailPath = "/customer-chance/detail";
        /** bidInfoSyncPath. */
        private String bidInfoSyncPath = "/customer-chance/bidInfoSync";
    }

    /**
     * 标讯关联 CRM 商机时的初始匹配策略。
     * - EXACT: 按招标主体 + 报名截止/开标时间精确匹配 evaluationTime
     * - GROUP: 按招标主体（groupName）匹配，查不到时兜底全量
     * - ALL:   直接拉取全量商机
     */
    private MatchingStrategy matchingStrategy = MatchingStrategy.GROUP;

    public enum MatchingStrategy {
        EXACT, GROUP, ALL
    }

    @Data
    public static class CrmContactPersonPaths {
        /** pageListPath. */
        private String pageListPath = "/contact-person-info/page-list";
    }

    @Data
    public static class CrmMessagePaths {
        /** sendPath. */
        private String sendPath = "/common/sendMessage";
    }
}
