package com.xiyu.bid.notification.dto;

import java.time.LocalDateTime;

public record NotificationSummary(
    Long id,
    Long notificationId,
    String type,
    String title,
    String body,
    String sourceEntityType,
    Long sourceEntityId,
    String payloadJson,
    boolean read,
    LocalDateTime createdAt
) {
}
