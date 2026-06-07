package com.xiyu.bid.notification.outbound.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;

public record NotificationDeliveryCommand(
        Long notificationId,
        Long recipientUserId,
        String type,
        String title,
        String sourceEntityType,
        Long sourceEntityId
) {
    public static NotificationDeliveryCommand fromEvent(NotificationCreatedEvent event, Long recipientUserId) {
        return new NotificationDeliveryCommand(
                event.notificationId(),
                recipientUserId,
                event.type(),
                event.title(),
                event.sourceEntityType(),
                event.sourceEntityId()
        );
    }
}
