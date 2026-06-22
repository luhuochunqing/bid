package com.xiyu.bid.crm.application;

/**
 * CRM 客户负责人查询结果。
 *
 * @param groupName        客户名（招标主体）
 * @param projectLeaderName 项目负责人姓名
 * @param projectLeaderNo   项目负责人工号
 */
public record CustomerLeaderResult(
        String groupName,
        String projectLeaderName,
        String projectLeaderNo
) {}
