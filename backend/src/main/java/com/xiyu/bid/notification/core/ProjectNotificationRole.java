package com.xiyu.bid.notification.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

/**
 * 项目级通知角色定义。
 *
 * <p>每个枚举值对应一种项目内通知接收人来源。部分角色绑定固定角色码，
 * 部分角色依赖项目实体字段或调用方传入参数。</p>
 *
 * <p>从 {@link com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicy}
 * 中独立出来，避免枚举被嵌套在策略类内部导致引用路径冗长，并便于跨模块复用。</p>
 */
public enum ProjectNotificationRole {
    /** 投标管理员：角色码 {@link RoleProfileCatalog#BID_ADMIN_CODE}。 */
    BID_ADMIN(RoleProfileCatalog.BID_ADMIN_CODE),
    /** 投标组长：角色码 {@link RoleProfileCatalog#BID_LEAD_CODE}。 */
    BID_TEAM_LEADER(RoleProfileCatalog.BID_LEAD_CODE),
    /** 主投标负责人：依赖项目投标负责人分配记录。 */
    BID_LEAD,
    /** 投标辅助人员：角色码 {@link RoleProfileCatalog#BID_SPECIALIST_CODE} + 副投标负责人。 */
    BID_ASSISTANT(RoleProfileCatalog.BID_SPECIALIST_CODE),
    /** 立项人/项目业主方负责人：依赖项目立项详情。 */
    PROJECT_OWNER,
    /** 任务执行人：由调用方显式传入 assigneeId。 */
    TASK_EXECUTOR,
    /** 标书审核人：依赖标书审核记录。 */
    BID_REVIEWER,
    /** 项目成员：{@code sys_project_member} 全员。 */
    PROJECT_MEMBER;

    private final String roleCode;

    ProjectNotificationRole() {
        this.roleCode = null;
    }

    ProjectNotificationRole(String roleCode) {
        this.roleCode = roleCode;
    }

    public String roleCode() {
        return roleCode;
    }
}
