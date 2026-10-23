// Input: projectId, operatorUserId, occurredAt
// Output: 触发 CRM 标讯回传（通过 WebhookDeliveryTask 异步可靠重试）
// Pos: project/domain/
package com.xiyu.bid.project.domain;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 立项关键信息变更事件（审核通过或立项后修改计划入围供应商数量/招标文件不利项）。
 * <p>由 {@code ProjectInitiationWebhookListener} 监听，入队 {@code WebhookDeliveryTask}，
 * 主动回传 CRM 商机 bidInfoSync 接口，保持投标系统与 CRM 商机数据一致。
 */
public record ProjectInitiationChangedEvent(
        Long projectId,
        Long operatorUserId,
        LocalDateTime occurredAt
) {
    public ProjectInitiationChangedEvent {
        Objects.requireNonNull(projectId, "projectId must not be null");
        if (occurredAt == null) {
            occurredAt = LocalDateTime.now();
        }
    }
}
