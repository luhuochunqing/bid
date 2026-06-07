package com.xiyu.bid.crm.infrastructure.dto;

/**
 * CRM 商机单条记录
 *
 * @param id       商机ID
 * @param title    商机标题/名称
 * @param customer 客户名称（可选）
 */
public record CrmOpportunityDto(
    String id,
    String title,
    String customer
) {}
