package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CRM 对接人单条记录 VO。
 * <p>对应客户接口 POST /contact-person-info/page-list 返回的 data 元素。
 */
public record ContactPersonInfoVO(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("phone") String phone,
    @JsonProperty("email") String email,
    @JsonProperty("ehsyProjectManager") String ehsyProjectManager,
    @JsonProperty("contacted") Boolean contacted,
    @JsonProperty("contactMethod") String contactMethod,
    @JsonProperty("preferenceLevel") String preferenceLevel,
    @JsonProperty("preferenceBasis") String preferenceBasis,
    @JsonProperty("seniorMeeting") Boolean seniorMeeting,
    @JsonProperty("guidedBidDocument") Boolean guidedBidDocument,
    @JsonProperty("getKeyInfo") Boolean getKeyInfo,
    @JsonProperty("deleteDisadvantage") Boolean deleteDisadvantage,
    @JsonProperty("syncInfo") Boolean syncInfo,
    @JsonProperty("guaranteeWin") Boolean guaranteeWin,
    @JsonProperty("impactRate") String impactRate,
    @JsonProperty("createBy") String createBy,
    @JsonProperty("createByName") String createByName,
    @JsonProperty("updateBy") String updateBy,
    @JsonProperty("updateByName") String updateByName,
    @JsonProperty("createAt") String createAt,
    @JsonProperty("updateAt") String updateAt
) {}
