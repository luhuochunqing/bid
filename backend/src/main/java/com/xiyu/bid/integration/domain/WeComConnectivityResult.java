package com.xiyu.bid.integration.domain;

import java.time.LocalDateTime;

/**
 * Immutable result of a WeCom connectivity probe.
 * Pure domain record — no Spring/JPA annotations.
 */
public record WeComConnectivityResult(
        boolean success,
        String message,
        LocalDateTime probedAt
) {
}
