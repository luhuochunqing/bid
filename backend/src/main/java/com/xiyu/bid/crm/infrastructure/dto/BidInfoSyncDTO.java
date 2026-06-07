package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * CRM 标讯回传请求。
 * <p>对应客户接口 POST /customer-chance/bidInfoSync 的请求体。
 */
public record BidInfoSyncDTO(
    @JsonProperty("bidInfoList") List<BidInfoInnerDTO> bidInfoList
) {}
