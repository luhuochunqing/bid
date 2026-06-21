// Input: 标讯状态变更回调 4.1 契约的评估建议
// Output: 回传 CRM 的评估建议 DTO
// Pos: webhook/domain/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.webhook.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 标讯状态变更回调的评估建议（4.1 契约 recommendation）。
 * <p>对应接口文档 §4.1 recommendation 字段，含 shouldBid 和 reason。
 * <p>当标讯无评估建议时为 null（JSON 省略）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RecommendationPayload(
        @JsonProperty("shouldBid") Boolean shouldBid,
        @JsonProperty("reason") String reason
) {}
