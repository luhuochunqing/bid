package com.xiyu.bid.approval.core;

import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.entity.User;

import java.util.Objects;

/**
 * 审批权限判定
 */
public class ApprovalPermissionPolicy {

    public ApprovalRuleResult canApprove(ApprovalRequest request, Long userId) {
        if (request.getCurrentApproverId() == null || request.getCurrentApproverId().equals(userId)) {
            return ApprovalRuleResult.allow();
        }
        return ApprovalRuleResult.deny("您没有权限审批此请求");
    }

    public ApprovalRuleResult canViewPendingQueue(Long currentUserId, User.Role currentUserRole, Long approverId) {
        if (approverId == null || isPrivileged(currentUserRole) || approverId.equals(currentUserId)) {
            return ApprovalRuleResult.allow();
        }
        return ApprovalRuleResult.deny("只能查看自己的待审批列表");
    }

    public ApprovalRuleResult canViewApprovalRequest(
            ApprovalRequest request,
            Long currentUserId,
            User.Role currentUserRole
    ) {
        if (isPrivileged(currentUserRole)
                || Objects.equals(request.getRequesterId(), currentUserId)
                || Objects.equals(request.getCurrentApproverId(), currentUserId)) {
            return ApprovalRuleResult.allow();
        }
        return ApprovalRuleResult.deny("无权查看该审批");
    }

    public ApprovalRuleResult canMarkAsRead(ApprovalRequest request, Long userId) {
        return Objects.equals(request.getCurrentApproverId(), userId)
                ? ApprovalRuleResult.allow()
                : ApprovalRuleResult.deny("只有当前审批人可以标记已读");
    }

    public boolean isPrivileged(User.Role role) {
        return role == User.Role.ADMIN || role == User.Role.MANAGER;
    }
}
