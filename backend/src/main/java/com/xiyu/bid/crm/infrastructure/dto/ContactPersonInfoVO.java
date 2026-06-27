package com.xiyu.bid.crm.infrastructure.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * CRM 对接人单条记录 VO。
 * <p>对应客户接口 POST /contact-person-info/page-list 返回的 data 元素。
 *
 * <p>CO-329：CRM 实际响应会带 VO 未定义的字段（如 {@code position}），Jackson 默认抛
 * {@code UnrecognizedPropertyException}，导致 {@code parseListResponse} catch 后返回空列表，
 * 客户信息矩阵对接人列带不过来。加 {@code ignoreUnknown=true} 忽略未知字段。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ContactPersonInfoVO(
    @JsonProperty("id") Long id,
    @JsonProperty("name") String name,
    @JsonProperty("phone") String phone,
    @JsonProperty("email") String email,
    @JsonProperty("position") String position,
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
