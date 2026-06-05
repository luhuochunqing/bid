package com.xiyu.bid.crm.application;

import com.xiyu.bid.crm.domain.AssignmentResult;

/**
 * CRM 项目客户端接口
 * 
 * <p>职责：根据业主单位名称查询 CRM 项目及负责人信息。
 * 当前为 Stub 实现，待真实 CRM API 就绪后替换。
 */
public interface CrmProjectClient {

    /**
     * 根据业主单位名称查询 CRM 项目信息。
     *
     * @param purchaserName 业主单位名称
     * @return 分配结果；匹配失败返回 AssignmentResult.noMatch()
     */
    AssignmentResult findProjectByPurchaser(String purchaserName);

    /**
     * Stub 实现：始终返回无匹配
     * 
     * <p>待真实 CRM API 就绪后，替换为实际 HTTP 调用实现。
     */
    CrmProjectClient STUB = purchaserName -> {
        // Stub 模式：不做实际调用，直接返回无匹配
        // 真实实现应调用 CRM API: POST /crm/project/search
        return AssignmentResult.noMatch();
    };
}
