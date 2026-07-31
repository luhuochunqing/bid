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
 * <p>{@code body}（企微文案丢失修复）从通知事件透传，用于让企微消息展示完整正文，
 * 而非仅展示 title。body 为 null 时（老版本兼容），回退到 type + title 组合展示。
 *
 * <p>{@link JsonInclude} 设置为 NON_NULL 是为了让序列化输出在未透传 targetUrl/body 时
 * 与老版本一致，减少 DB 中 payload 字段格式差异。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationDeliveryCommand(
        Long notificationId,
        Long recipientUserId,
        String type,
        String title,
        String body,
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
                event.body(),
                event.sourceEntityType(),
                event.sourceEntityId(),
                event.targetUrl()
        );
    }
}
