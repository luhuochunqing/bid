package com.xiyu.bid.crm.infrastructure.dto;

import java.util.List;

/**
 * CRM 商机列表响应包装
 */
public record CrmOpportunityListResponse(
    List<CrmOpportunityDto> opportunities
) {}
