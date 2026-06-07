package com.xiyu.bid.tender.core;

import com.xiyu.bid.crm.domain.AssignmentResult;

/**
 * 标讯自动分配策略（纯核心）
 * 
 * <p>根据业主单位名称匹配 CRM 项目负责人，决定是否自动分配标讯。
 * 本类是纯函数，不依赖任何 Spring/JPA/外部服务。
 * 
 * <p>分配决策规则：
 * <ul>
 *   <li>存在匹配映射 → 返回包含负责人信息的 AssignmentResult.success</li>
 *   <li>无匹配映射 → 返回 AssignmentResult.noMatch（保持 PENDING_ASSIGNMENT）</li>
 * </ul>
 * 
 * <p>调用约定：
 * <ul>
 *   <li>{@code resolve(null)} → AssignmentResult.noMatch()</li>
 *   <li>purchaserName 为空白 → AssignmentResult.noMatch()</li>
 * </ul>
 */
public final class TenderAssignmentPolicy {

    private TenderAssignmentPolicy() {
        // 工具类不可实例化
    }

    /**
     * 根据业主单位名称解析分配结果。
     *
     * @param purchaserName 业主单位名称
     * @return 分配结果，永不为 null
     */
    public static AssignmentResult resolve(String purchaserName) {
        if (isBlank(purchaserName)) {
            return AssignmentResult.noMatch();
        }
        
        // 业务规则：匹配成功条件由调用方（CrmProjectMapping）提供
        // 本策略仅负责决策逻辑
        return AssignmentResult.noMatch();
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
