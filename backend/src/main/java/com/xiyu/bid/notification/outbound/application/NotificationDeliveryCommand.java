package com.xiyu.bid.notification.outbound.application;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;

/**
 * 单次企微投递命令。
 *
 * <p>{@code targetUrl}（P0-1 修复）从通知 payload 透传，用于覆盖默认的
 * sourceEntityType → 路径映射。Jackson 反序列化老 payload 时该字段为 null，
 * 外发回退到 entityType 映射，向后兼容。
 *
 * <p>{@link JsonInclude} 设置为 NON_NULL 是为了让序列化输出在未透传 targetUrl 时
 * 与老版本一致，减少 DB 中 payload 字段格式差异。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDeliveryCommand(
        Long notificationId,
        Long recipientUserId,
        String type,
        String title,
        String sourceEntityType,
        Long sourceEntityId,
        String targetUrl
) {
    public static NotificationDeliveryCommand fromEvent(NotificationCreatedEvent event, Long recipientUserId) {
        return new NotificationDeliveryCommand(
                event.notificationId(),
                recipientUserId,
                event.type(),
                event.title(),
                event.sourceEntityType(),
                event.sourceEntityId(),
                event.targetUrl()
        );
    }
}
