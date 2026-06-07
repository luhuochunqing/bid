package com.xiyu.bid.crm.infrastructure.dto;

import java.util.List;
import java.util.Map;

/**
 * CRM 商机关联的评估表三段数据 DTO。
 *
 * @param opportunityId    CRM 商机 ID
 * @param basic            基础信息段（字段名 → 值）
 * @param customerInfos    客户信息段 EAV 行（roleKey + 各 infoKey → value）
 * @param recommendation   投标建议段（shouldBid + reason）
 */
public record CrmEvaluationDataDto(
    String opportunityId,
    Map<String, Object> basic,
    List<Map<String, String>> customerInfos,
    Map<String, Object> recommendation
) {}
