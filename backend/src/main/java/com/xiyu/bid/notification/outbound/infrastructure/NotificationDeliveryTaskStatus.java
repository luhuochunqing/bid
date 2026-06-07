package com.xiyu.bid.notification.outbound.infrastructure;

public enum NotificationDeliveryTaskStatus {
    PENDING,
    PROCESSING,
    PENDING_RETRY,
    DELIVERED,
    DEAD_LETTER
}
