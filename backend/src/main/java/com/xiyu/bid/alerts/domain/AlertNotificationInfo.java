// Input: 告警类型、告警消息、关联 ID、附加载荷
// Output: 通知发送所需的结构化信息（通知类型、标题、正文、来源、目标 URL）
// Pos: alerts/domain - 纯核心通知信息载体（不可变 record）
package com.xiyu.bid.alerts.domain;

/**
 * 告警通知信息载体：由 {@link AlertMessagePolicy} 生成，描述一条告警通知的
 * 通知类型、标题、正文、来源实体、目标 URL 等结构化字段。
 *
 * <p>本类为纯核心 record，不可变，无副作用。另一个 agent 将在纯核心层
 * 完善具体的字段语义和构建规则。</p>
 *
 * @param notificationType  通知类型字符串（对应 {@code NotificationType} 枚举名）
 * @param title             通知标题
 * @param body              通知正文
 * @param sourceEntityType  来源实体类型（可为 null）
 * @param sourceEntityId    来源实体 ID（可为 null）
 * @param targetUrl         目标跳转 URL（可为 null）
 */
public record AlertNotificationInfo(
    String notificationType,
    String title,
    String body,
    String sourceEntityType,
    Long sourceEntityId,
    String targetUrl
) {
}
