package com.xiyu.bid.webhook.application;

import java.time.LocalDateTime;

public record WebhookSendResult(
        boolean successful,
        Integer statusCode,
        String responseBody,
        String errorMessage,
        LocalDateTime respondedAt
) {
    public static WebhookSendResult success(int statusCode, String responseBody, LocalDateTime respondedAt) {
        return new WebhookSendResult(true, statusCode, responseBody, null, respondedAt);
    }

    public static WebhookSendResult failure(Integer statusCode, String responseBody, String errorMessage, LocalDateTime respondedAt) {
        return new WebhookSendResult(false, statusCode, responseBody, errorMessage, respondedAt);
    }
}
