// Input: 项目结果确认回调 4.2 契约的竞争对手信息
// Output: 回传 CRM 的竞争对手 DTO
// Pos: crm/infrastructure/dto/
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 项目结果确认回调的竞争对手信息（4.2 契约 Competitor）。
 * <p>对应接口文档 §4.2 competitors 数组元素。四字段均为非必填，空行不传。
 */
public record CompetitorInfo(
        @JsonProperty("name") String name,
        @JsonProperty("discount") String discount,
        @JsonProperty("paymentTerm") String paymentTerm,
        @JsonProperty("notes") String notes
) {}
