// Input: 业务场景类型 + 可选预过滤条件
// Output: 不可变的上下文值对象
// Pos: Core/纯核心（无 Spring 依赖）
// 维护声明: 纯值对象，不依赖框架 / IO / 状态；AssignmentCandidateAppService 据此编排调用链。
package com.xiyu.bid.user.core;

import java.util.Set;

/**
 * 分配候选人查询上下文（Pure Core — no Spring dependencies）。
 *
 * <p>contextType 标识业务场景（"task" 或 "tender"），deptCode / roleCode
 * 为可选的预过滤参数，允许调用方在编排前收窄候选范围。
 */
public record AssignmentContext(
        String contextType,
        String deptCode,
        String roleCode
) {

    // P1.2: 场景化角色排除规则——避免无业务意义的角色出现在候选人列表中
    private static final Set<String> TASK_EXCLUDED_ROLES = Set.of("staff");
    private static final Set<String> TENDER_EXCLUDED_ROLES = Set.of("staff", "admin_staff");

    public static AssignmentContext of(String contextType, String deptCode, String roleCode) {
        return new AssignmentContext(contextType, deptCode, roleCode);
    }

    public boolean isValidContextType() {
        return "task".equalsIgnoreCase(contextType) || "tender".equalsIgnoreCase(contextType);
    }

    public boolean isTaskContext() {
        return "task".equalsIgnoreCase(contextType);
    }

    public boolean isTenderContext() {
        return "tender".equalsIgnoreCase(contextType);
    }

    /**
     * P1.2: 根据业务场景判断角色是否应被排除。
     * <ul>
     *   <li>task: 排除 staff（普通员工不可被分配任务）</li>
     *   <li>tender: 排除 staff + admin_staff（行政人员不参与投标）</li>
     * </ul>
     */
    public boolean isExcludedRole(String roleCode) {
        if (roleCode == null) {
            return false;
        }
        Set<String> excluded = isTaskContext() ? TASK_EXCLUDED_ROLES : TENDER_EXCLUDED_ROLES;
        return excluded.contains(roleCode.toLowerCase());
    }
}
