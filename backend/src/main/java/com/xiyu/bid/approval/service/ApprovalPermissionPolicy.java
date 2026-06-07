package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.entity.User;

import java.util.Objects;

final class ApprovalPermissionPolicy {

    private ApprovalPermissionPolicy() {
    }

    static ApprovalDecisionPolicy.Decision canReadPendingQueue(Long currentUserId, User.Role currentUserRole, Long approverId) {
        if (approverId != null && !isPrivileged(currentUserRole) && !approverId.equals(currentUserId)) {
            return ApprovalDecisionPolicy.Decision.rejected("只能查看自己的待审批列表");
        }
        return ApprovalDecisionPolicy.Decision.allowed();
    }

    static ApprovalDecisionPolicy.Decision canApprove(ApprovalRequest request, Long approverId) {
        if (request.getCurrentApproverId() == null || request.getCurrentApproverId().equals(approverId)) {
            return ApprovalDecisionPolicy.Decision.allowed();
        }
        return ApprovalDecisionPolicy.Decision.rejected("您没有权限审批此请求");
    }

    static ApprovalDecisionPolicy.Decision canView(ApprovalRequest request, Long currentUserId, User.Role currentUserRole) {
        if (isPrivileged(currentUserRole)
                || Objects.equals(request.getRequesterId(), currentUserId)
                || Objects.equals(request.getCurrentApproverId(), currentUserId)) {
            return ApprovalDecisionPolicy.Decision.allowed();
        }
        return ApprovalDecisionPolicy.Decision.rejected("无权查看该审批");
    }

    static ApprovalDecisionPolicy.Decision canMarkRead(ApprovalRequest request, Long userId) {
        return Objects.equals(request.getCurrentApproverId(), userId)
                ? ApprovalDecisionPolicy.Decision.allowed()
                : ApprovalDecisionPolicy.Decision.rejected("只有当前审批人可以标记已读");
    }

    static boolean isPrivileged(User.Role role) {
        return role == User.Role.ADMIN || role == User.Role.MANAGER;
    }
}
