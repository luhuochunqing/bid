// Input: 项目结果确认登记成功后的领域事件
// Output: 触发 §4.2 CRM 回调（通过 WebhookDeliveryTask 队列异步重试）
// Pos: project/domain/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.domain;

import com.xiyu.bid.project.core.BidResultType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 项目结果确认登记成功领域事件（接口文档 §4.2）。
 * <p>触发时机：用户在结果确认页提交并持久化成功后发布。
 * <p>由 {@code ProjectResultConfirmedWebhookListener} 监听，入队 {@code WebhookDeliveryTask}
 * 走统一的 1min/5min/15min 重试机制（与 §4.1 标讯状态变更回调一致）。
 * <p>载荷用原始字段而非 Controller DTO，避免分层违规。
 */
public record ProjectResultConfirmedEvent(
        Long projectId,
        Long tenderId,
        BidResultType resultType,
        List<Long> evidenceFileIds,
        List<CompetitorSnapshot> competitors,
        Long operatorUserId,
        String operatorName,
        Long resultId,
        LocalDateTime occurredAt
) {
    /**
     * 竞争对手快照（原始字段，不依赖 Controller DTO）。
     */
    public record CompetitorSnapshot(
            String name,
            String discount,
            String paymentTerm,
            String notes
    ) {}

    public static ProjectResultConfirmedEvent of(Long projectId, Long tenderId, BidResultType resultType,
                                                  List<Long> evidenceFileIds,
                                                  List<CompetitorSnapshot> competitors,
                                                  Long operatorUserId, String operatorName, Long resultId) {
        return new ProjectResultConfirmedEvent(projectId, tenderId, resultType, evidenceFileIds,
                competitors, operatorUserId, operatorName, resultId, LocalDateTime.now());
    }
}
