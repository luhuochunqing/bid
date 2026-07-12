package com.xiyu.bid.webhook.domain;

import com.xiyu.bid.entity.Tender;

import java.time.LocalDateTime;

/**
 * 标讯状态变更领域事件。触发 outbound webhook 推送。
 */
public record TenderStatusChangedEvent(
    Long tenderId,
    String externalId,
    Tender.Status oldStatus,
    Tender.Status newStatus,
    String title,
    LocalDateTime occurredAt,
    String abandonReason,
    Long operatorId,
    String operatorName,
    Boolean recommendationShouldBid,
    String recommendationReason
) {
    // CO-576 Phase C: 5 参/6 参 factory 已删除，所有调用点必须使用完整 10 参 factory，
    // 确保 operatorId + operatorName 不会缺失（避免 webhook 空 username 死信）。
    public static TenderStatusChangedEvent of(Long tenderId, String externalId,
                                               Tender.Status oldStatus, Tender.Status newStatus,
                                               String title, String abandonReason,
                                               Long operatorId, String operatorName,
                                               Boolean recommendationShouldBid, String recommendationReason) {
        return new TenderStatusChangedEvent(tenderId, externalId, oldStatus, newStatus, title, LocalDateTime.now(),
                abandonReason, operatorId, operatorName, recommendationShouldBid, recommendationReason);
    }
}
