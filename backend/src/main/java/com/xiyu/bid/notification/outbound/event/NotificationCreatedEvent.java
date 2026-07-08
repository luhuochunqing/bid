// Input: notification id + recipient ids + descriptive fields + payload targetUrl
// Output: event payload consumed by outbound push listeners
// Pos: Event/通知创建领域事件
//
// targetUrl 字段（P0-1 修复）：从 notification payload 中提取的深链，用于企微外发时
// 覆盖默认的 sourceEntityType → 路径映射。某些通知（如文档变更）的合理跳转目标不是
// sourceEntityType 对应的实体页，而是关联项目的子页面——targetUrl 让外发链路能精确跳转。
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
    Long sourceEntityId,
    String targetUrl
) {

    /**
     * 向后兼容：老调用方未透传 targetUrl 时，使用 7 参数构造器，targetUrl 为 null。
     * 企微外发会回退到 sourceEntityType → 路径映射。
     */
    public NotificationCreatedEvent(
        Long notificationId,
        List<Long> recipientUserIds,
        String type,
        String title,
        String sourceEntityType,
        Long sourceEntityId
    ) {
        this(notificationId, recipientUserIds, type, title, sourceEntityType, sourceEntityId, null);
    }

    public NotificationCreatedEvent {
        recipientUserIds = recipientUserIds == null ? List.of() : List.copyOf(recipientUserIds);
    }
}
