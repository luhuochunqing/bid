// Input: notification id + recipient ids + descriptive fields
// Output: event payload consumed by outbound push listeners
// Pos: Event/通知创建领域事件
package com.xiyu.bid.notification.outbound.event;

import java.util.List;

/**
 * Immutable domain event published AFTER_COMMIT of notification creation.
 *
 * <p>The canonical constructor defensively copies {@code recipientUserIds}
 * into an immutable list so downstream consumers cannot mutate the event and
 * so callers need not remember to copy before publishing.
 */
public record NotificationCreatedEvent(
    Long notificationId,
    List<Long> recipientUserIds,
    String type,
    String title,
    String sourceEntityType,
    Long sourceEntityId
) {
    public NotificationCreatedEvent {
        recipientUserIds = recipientUserIds == null ? List.of() : List.copyOf(recipientUserIds);
    }
}
