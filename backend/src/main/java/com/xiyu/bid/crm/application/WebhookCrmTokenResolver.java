package com.xiyu.bid.crm.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook CRM token：委托 {@link CrmAuthService}（文档三步，仅用户 OSS→CRM JWT）。
 * <p>无 operator_username → {@link TokenUnavailableException}（无系统账号可用）。
 */
@Component
public class WebhookCrmTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(WebhookCrmTokenResolver.class);

    private final CrmAuthService crmAuthService;

    public WebhookCrmTokenResolver(CrmAuthService crmAuthService) {
        this.crmAuthService = crmAuthService;
    }

    /**
     * @param username 操作者 username（必填）
     */
    public String resolveToken(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException(
                    "Webhook needs operator username (OSS→CRM JWT requires a real user login; no system account)");
        }
        return crmAuthService.getValidTokenForUser(username);
    }

    public String getValidTokenForUserStrict(String username) {
        return resolveToken(username);
    }

    public void handleUnauthorizedForUser(String username) {
        crmAuthService.handleUnauthorizedForUser(username);
        log.debug("Webhook unauthorized: cleared user token chain for username={}", username);
    }
}
