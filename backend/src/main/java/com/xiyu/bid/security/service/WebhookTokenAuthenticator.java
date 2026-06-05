// Input: configured shared-secret token + provided header value
// Output: boolean validity check, fail-closed when secret is unset
// Pos: Service/webhook 共享密钥校验
package com.xiyu.bid.security.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
public class WebhookTokenAuthenticator {

    public static final String CRM_HEADER = "X-CRM-Webhook-Token";

    private final String crmToken;

    public WebhookTokenAuthenticator(@Value("${webhook.crm.token:}") String crmToken) {
        this.crmToken = crmToken == null ? "" : crmToken.trim();
    }

    public boolean isValidCrmToken(String providedToken) {
        if (!StringUtils.hasText(crmToken) || providedToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
            crmToken.getBytes(StandardCharsets.UTF_8),
            providedToken.getBytes(StandardCharsets.UTF_8)
        );
    }
}
