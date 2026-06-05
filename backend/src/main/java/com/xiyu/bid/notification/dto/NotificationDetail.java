package com.xiyu.bid.notification.dto;

import java.time.LocalDateTime;

public record NotificationDetail(
    Long id,
    Long notificationId,
    String type,
    String title,
    String body,
    String sourceEntityType,
    Long sourceEntityId,
    boolean read,
    LocalDateTime createdAt,
    String payloadJson
) {
}
