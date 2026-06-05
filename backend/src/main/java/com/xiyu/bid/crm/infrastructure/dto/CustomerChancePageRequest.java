package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CRM 商机列表分页查询请求。
 * <p>对应客户接口 POST /customer-chance/page-list 的完整请求体。
 */
public record CustomerChancePageRequest(
    @JsonProperty("pageIndex") int pageIndex,
    @JsonProperty("pageSize") int pageSize,
    @JsonProperty("body") CustomerChanceDTO body
) {}
