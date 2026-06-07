package com.xiyu.bid.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Response DTO for WeCom integration configuration.
 * corpSecret is NEVER included — only secretConfigured (boolean) is returned.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record WeComIntegrationResponse(
        boolean configured,
        String corpId,
        String agentId,
        boolean secretConfigured,
        boolean ssoEnabled,
        boolean messageEnabled,
        String notifyUserIds
) {

    public static WeComIntegrationResponse empty() {
        return new WeComIntegrationResponse(false, null, null, false, false, false, null);
    }

    public static WeComIntegrationResponse configured(
            String corpId,
            String agentId,
            boolean ssoEnabled,
            boolean messageEnabled,
            String notifyUserIds
    ) {
        return new WeComIntegrationResponse(true, corpId, agentId, true, ssoEnabled, messageEnabled, notifyUserIds);
    }
}
