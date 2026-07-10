package com.xiyu.bid.crm.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Webhook CRM token 解析（委托 {@link CrmAuthService}，避免双实现漂移）。
 * <p>有 operator → 用户路径；无 operator → 系统集成账号路径（显式，非暗门）。
 */
@Component
public class WebhookCrmTokenResolver {

    private static final Logger log = LoggerFactory.getLogger(WebhookCrmTokenResolver.class);

    private final CrmAuthService crmAuthService;

    public WebhookCrmTokenResolver(CrmAuthService crmAuthService) {
        this.crmAuthService = crmAuthService;
    }

    /**
     * @param username 操作者；blank 时走系统集成账号
     */
    public String resolveToken(String username) {
        return crmAuthService.getValidTokenForCaller(username);
    }

    /** @deprecated 使用 {@link #resolveToken(String)}；保留方法名兼容旧调用 */
    public String getValidTokenForUserStrict(String username) {
        if (username == null || username.isBlank()) {
            throw new TokenUnavailableException(
                    "Webhook strict user token requires operator username");
        }
        return crmAuthService.getValidTokenForUser(username);
    }

    public void handleUnauthorizedForUser(String username) {
        crmAuthService.handleUnauthorizedForCaller(username);
        log.debug("Webhook unauthorized handled for username={}", username);
    }
}
