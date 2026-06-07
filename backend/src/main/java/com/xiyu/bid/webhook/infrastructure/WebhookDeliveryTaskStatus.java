package com.xiyu.bid.webhook.infrastructure;

public enum WebhookDeliveryTaskStatus {
    PENDING,
    PROCESSING,
    PENDING_RETRY,
    DELIVERED,
    DEAD_LETTER
}
