package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.RoleProfileCatalog;

import java.util.Set;

public final class TaskOperationPolicy {

    private static final Set<String> DIRECT_MANAGE_ROLES = Set.of(
            RoleProfileCatalog.ADMIN_CODE,
            RoleProfileCatalog.BID_ADMIN_CODE,
            RoleProfileCatalog.BID_LEAD_CODE
    );

    private TaskOperationPolicy() {
    }

    public static TaskOperationDecision canManageTask(
            String roleCode,
            Long currentUserId,
            Long primaryLeadId,
            Long secondaryLeadId
    ) {
        if (roleCode != null && DIRECT_MANAGE_ROLES.contains(roleCode)) {
            return TaskOperationDecision.permit();
        }

        if (RoleProfileCatalog.SALES_CODE.equals(roleCode)) {
            if (currentUserId != null && currentUserId.equals(primaryLeadId)) {
                return TaskOperationDecision.permit();
            }
            return TaskOperationDecision.deny("投标项目负责人仅可管理自己作为负责人的项目任务");
        }

        if (RoleProfileCatalog.BID_SPECIALIST_CODE.equals(roleCode)) {
            if (currentUserId != null
                    && (currentUserId.equals(primaryLeadId) || currentUserId.equals(secondaryLeadId))) {
                return TaskOperationDecision.permit();
            }
            return TaskOperationDecision.deny("投标专员仅可管理自己作为负责人或辅助负责人的项目任务");
        }

        return TaskOperationDecision.deny("当前角色无权管理任务");
    }

    public static TaskOperationDecision canSubmitTask(
            String roleCode,
            Long assigneeId,
            Long currentUserId
    ) {
        if (assigneeId != null && assigneeId.equals(currentUserId)) {
            return TaskOperationDecision.permit();
        }
        return TaskOperationDecision.deny("仅任务执行人本人可提交任务");
    }

    public static TaskOperationDecision canUploadDeliverable(
            String roleCode,
            Long assigneeId,
            Long currentUserId
    ) {
        if (assigneeId != null && assigneeId.equals(currentUserId)) {
            return TaskOperationDecision.permit();
        }
        return TaskOperationDecision.deny("仅任务执行人本人可上传交付物");
    }

    public static TaskOperationDecision canReviewTask(
            String roleCode,
            Long currentUserId,
            Long primaryLeadId,
            Long secondaryLeadId
    ) {
        return canManageTask(roleCode, currentUserId, primaryLeadId, secondaryLeadId);
    }
}
