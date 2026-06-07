// Input: UserNotification entity (with eagerly/lazily loaded Notification)
// Output: NotificationSummary / NotificationDetail records
// Pos: DTO/通知装配器
package com.xiyu.bid.notification.dto;

import com.xiyu.bid.notification.entity.Notification;
import com.xiyu.bid.notification.entity.UserNotification;

/**
 * Static utility that maps UserNotification entities to API response records.
 *
 * <p>Lives in the dto package so controllers/services can call it without
 * dragging entities into response payloads.
 */
public final class NotificationAssembler {

    private NotificationAssembler() {
    }

    public static NotificationSummary toSummary(UserNotification un) {
        Notification notification = un.getNotification();
        return new NotificationSummary(
            un.getId(),
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getBody(),
            notification.getSourceEntityType(),
            notification.getSourceEntityId(),
            un.getReadAt() != null,
            un.getCreatedAt()
        );
    }

    public static NotificationDetail toDetail(UserNotification un) {
        Notification notification = un.getNotification();
        return new NotificationDetail(
            un.getId(),
            notification.getId(),
            notification.getType(),
            notification.getTitle(),
            notification.getBody(),
            notification.getSourceEntityType(),
            notification.getSourceEntityId(),
            un.getReadAt() != null,
            un.getCreatedAt(),
            notification.getPayloadJson()
        );
    }
}
