package com.xiyu.bid.tender.dto;

/**
 * 客户信息段 EAV 单元格 DTO（V130 三段式）。
 *
 * <p>对应 TenderEvaluationCustomerInfo 实体的单行记录，
 * 包含角色键、信息维度键、值和值类型。
 */
public record EvaluationCustomerInfoDTO(

    /** 角色枚举键（13 个固定角色之一）。 */
    String roleKey,

    /** 信息维度枚举键（14 个固定维度之一）。 */
    String infoKey,

    /** 单元格值。 */
    String value,

    /** 值类型：TEXT 或 DROPDOWN。 */
    String valueType
) {}
