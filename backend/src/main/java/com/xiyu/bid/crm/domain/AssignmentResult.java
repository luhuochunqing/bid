package com.xiyu.bid.crm.domain;

/**
 * 分配结果值对象
 * 封装自动分配决策的结果，包含匹配状态和负责人信息
 */
public record AssignmentResult(
        boolean matched,
        String crmProjectId,
        String projectManagerId,
        String projectManagerName,
        String departmentId,
        String departmentName
) {
    /**
     * 匹配成功
     */
    public static AssignmentResult success(
            String crmProjectId,
            String projectManagerId,
            String projectManagerName,
            String departmentId,
            String departmentName) {
        return new AssignmentResult(
                true,
                crmProjectId,
                projectManagerId,
                projectManagerName,
                departmentId,
                departmentName);
    }

    /**
     * 无匹配
     */
    public static AssignmentResult noMatch() {
        return new AssignmentResult(false, null, null, null, null, null);
    }

    public boolean isMatched() {
        return matched;
    }
}
