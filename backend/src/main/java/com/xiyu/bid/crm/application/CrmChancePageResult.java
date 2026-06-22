package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.infrastructure.dto.CustomerChanceVO;

import java.util.List;

/**
 * CRM 商机列表分页查询结果。
 *
 * @param list       商机列表
 * @param totalCount 总记录数
 * @param pageSize   每页大小
 * @param pageIndex  当前页码
 */
public record CrmChancePageResult(
        List<CustomerChanceVO> list,
        int totalCount,
        int pageSize,
        int pageIndex
) {}
