package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CRM 对接人列表查询请求。
 * <p>对应客户接口 POST /contact-person-info/page-list 的请求体。
 * 
 * @param ccId 商机 ID
 */
public record ContactPersonListDTO(
    @JsonProperty("ccId") Long ccId
) {}
