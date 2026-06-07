package com.xiyu.bid.integration.dto;

import java.time.LocalDateTime;

/**
 * Response DTO for WeCom connectivity probe result.
 */
public record WeComConnectivityResponse(
        boolean success,
        String message,
        LocalDateTime probedAt
) {
}
