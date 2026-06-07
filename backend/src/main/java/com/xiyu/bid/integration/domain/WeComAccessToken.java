package com.xiyu.bid.integration.domain;

import java.time.Instant;

/**
 * Immutable value object representing a WeCom access token with its expiry.
 * Pure domain record — no Spring/JPA annotations.
 */
public record WeComAccessToken(
        String token,
        Instant expiresAt
) {

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
}
