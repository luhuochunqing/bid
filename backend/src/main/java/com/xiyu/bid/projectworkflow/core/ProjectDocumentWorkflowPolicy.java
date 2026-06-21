// Input: 当前操作者角色 code
// Output: boolean / Decision — 是否允许删除项目文档
// Pos: projectworkflow/core/ - pure core policy, no Spring/JPA
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.projectworkflow.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * 项目文档工作流授权策略。
 * <p>纯核心：不依赖数据库、I/O、Spring 或日志。判断当前角色是否允许对项目文档执行删除等敏感操作。</p>
 */
public final class ProjectDocumentWorkflowPolicy {

    private ProjectDocumentWorkflowPolicy() {
    }

    /**
     * 校验指定角色是否有权删除项目文档。
     * <p>当前仅系统管理员（{@code admin}）和投标部门管理员（{@code bid_admin}）允许删除。</p>
     *
     * @param roleCode 当前操作者角色 code（可为 null）
     * @return 授权决策结果
     */
    public static Decision canDeleteProjectDocument(String roleCode) {
        if (roleCode == null) {
            return Decision.deny("当前用户未分配角色，无权删除文档");
        }
        String normalized = roleCode.trim().toLowerCase();
        if (RoleProfileCatalog.ADMIN_CODE.equals(normalized)
                || RoleProfileCatalog.BID_ADMIN_CODE.equals(normalized)) {
            return Decision.permit();
        }
        return Decision.deny("权限不足，仅管理员允许删除文档");
    }

    /**
     * 授权决策结果。
     *
     * @param allowed 是否允许
     * @param reason  拒绝原因描述（allowed=true 时为 null）
     */
    public record Decision(boolean allowed, String reason) {

        public static Decision permit() {
            return new Decision(true, null);
        }

        public static Decision deny(String reason) {
            return new Decision(false, reason);
        }
    }
}
