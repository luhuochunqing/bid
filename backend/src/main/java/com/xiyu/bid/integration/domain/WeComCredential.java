package com.xiyu.bid.integration.domain;

/**
 * Immutable value object representing WeCom (企业微信) credentials.
 * No Spring or JPA annotations — pure domain record.
 *
 * corpSecret is stored in plaintext here (in memory only).
 * The application layer is responsible for encrypting it before persistence.
 */
public record WeComCredential(
        String corpId,
        String agentId,
        String corpSecret,
        boolean ssoEnabled,
        boolean messageEnabled
) {
    @Override
    public String toString() {
        return "WeComCredential[corpId=%s, agentId=%s, ssoEnabled=%s, messageEnabled=%s, corpSecret=***]"
                .formatted(corpId, agentId, ssoEnabled, messageEnabled);
    }
}
