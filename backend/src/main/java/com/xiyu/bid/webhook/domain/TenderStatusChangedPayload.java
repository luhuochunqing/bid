// Input: 标讯状态变更回调 4.1 契约的请求体
// Output: 回传 CRM 的标讯状态变更载荷 DTO
// Pos: webhook/domain/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 标讯状态变更回调请求体（4.1 契约）。
 * <p>本平台在标讯状态发生变化时，向外部系统推送回调通知。
 * <p>触发时机：标讯提交投标（PENDING → BIDDING）或弃标（→ ABANDONED）时。
 * <p>顶层字段：
 * <ul>
 *   <li>{@code event} — 事件类型，固定值 "tender.status_changed"</li>
 *   <li>{@code tenderId} — 标讯 ID</li>
 *   <li>{@code sourceId} — 来源系统的数据唯一 ID（从 externalId 提取，与标讯推送接口一致）</li>
 *   <li>{@code oldStatus} — 变更前状态</li>
 *   <li>{@code newStatus} — 变更后状态</li>
 *   <li>{@code title} — 标讯标题</li>
 *   <li>{@code occurredAt} — 发生时间（ISO 8601）</li>
 *   <li>{@code operatorId} — 操作人 ID</li>
 *   <li>{@code operatorName} — 操作人姓名</li>
 *   <li>{@code abandonReason} — 弃标原因（仅弃标时返回，其他状态省略）</li>
 *   <li>{@code recommendation} — 评估建议（含 shouldBid 和 reason，无评估时省略）</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TenderStatusChangedPayload(
        @JsonProperty("event") String event,
        @JsonProperty("tenderId") Long tenderId,
        @JsonProperty("sourceId") String sourceId,
        @JsonProperty("oldStatus") String oldStatus,
        @JsonProperty("newStatus") String newStatus,
        @JsonProperty("title") String title,
        @JsonProperty("occurredAt") String occurredAt,
        @JsonProperty("operatorId") Long operatorId,
        @JsonProperty("operatorName") String operatorName,
        @JsonProperty("abandonReason") String abandonReason,
        @JsonProperty("recommendation") RecommendationPayload recommendation
) {}
