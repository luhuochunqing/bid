package com.xiyu.bid.task.core;

import com.xiyu.bid.common.domain.AuthorizationDecision;
import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.TreeSet;

public final class TaskOperationPolicy {

    // 复用 RoleProfileCatalog.GLOBAL_ACCESS_ROLES 的语义，使用大小写不敏感 TreeSet
    // （OSS 同步可能传入不同大小写的 roleCode，如 "/bidadmin" vs "/bidAdmin"）
    private static final TreeSet<String> DIRECT_MANAGE_ROLES = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    static {
        DIRECT_MANAGE_ROLES.addAll(RoleProfileCatalog.GLOBAL_ACCESS_ROLES);
    }

    private TaskOperationPolicy() {
    }

    public static AuthorizationDecision canManageTask(
            String roleCode,
            Long currentUserId,
            Long primaryLeadId,
            Long secondaryLeadId
    ) {
        if (roleCode != null && DIRECT_MANAGE_ROLES.contains(roleCode)) {
            return AuthorizationDecision.permit();
        }

        if (RoleProfileCatalog.SALES_CODE.equalsIgnoreCase(roleCode)) {
            if (currentUserId != null && currentUserId.equals(primaryLeadId)) {
                return AuthorizationDecision.permit();
            }
            return AuthorizationDecision.deny("投标项目负责人仅可管理自己作为负责人的项目任务");
        }

        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equalsIgnoreCase(roleCode)) {
            if (currentUserId != null
                    && (currentUserId.equals(primaryLeadId) || currentUserId.equals(secondaryLeadId))) {
                return AuthorizationDecision.permit();
            }
            return AuthorizationDecision.deny("投标专员仅可管理自己作为负责人或辅助负责人的项目任务");
        }

        return AuthorizationDecision.deny("当前角色无权管理任务");
    }

    /**
     * 校验任务执行人身份 — 仅执行人本人可提交/上传交付物。
     * <p>注意：角色不影响此权限，仅身份匹配判定。</p>
     *
     * @param assigneeId   任务指派人 ID
     * @param currentUserId 当前用户 ID
     * @return 授权决策
     */
    public static AuthorizationDecision canActAsAssignee(Long assigneeId, Long currentUserId) {
        if (assigneeId != null && assigneeId.equals(currentUserId)) {
            return AuthorizationDecision.permit();
        }
        return AuthorizationDecision.deny("仅任务执行人本人可执行此操作");
    }

    /**
     * 校验审核权限 — 管理权限持有者可审核，但不能审核自己提交的任务。
     * <p>职责分离原则：提交者不应审核自己提交的工作。</p>
     *
     * @param roleCode       当前操作者角色 code
     * @param currentUserId  当前用户 ID
     * @param primaryLeadId  项目主负责人 ID
     * @param secondaryLeadId 项目副负责人 ID
     * @param assigneeId     任务执行人 ID（用于防止自审）
     * @return 授权决策
     */
    public static AuthorizationDecision canReviewTask(
            String roleCode,
            Long currentUserId,
            Long primaryLeadId,
            Long secondaryLeadId,
            Long assigneeId
    ) {
        // 职责分离：不能审核自己提交的任务
        if (currentUserId != null && currentUserId.equals(assigneeId)) {
            return AuthorizationDecision.deny("不能审核自己提交的任务");
        }
        return canManageTask(roleCode, currentUserId, primaryLeadId, secondaryLeadId);
    }
}
