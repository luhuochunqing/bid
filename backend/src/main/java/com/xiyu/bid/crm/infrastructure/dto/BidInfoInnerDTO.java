package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CRM 标讯回传单条记录。
 * <p>对应客户接口 POST /customer-chance/bidInfoSync 的 bidInfoList 元素。
 */
public record BidInfoInnerDTO(
    @JsonProperty("name") String name,
    @JsonProperty("code") String code,
    @JsonProperty("status") Integer status,
    @JsonProperty("statusEditor") String statusEditor,
    @JsonProperty("statusEditTime") String statusEditTime,
    @JsonProperty("feedback") String feedback
) {}
