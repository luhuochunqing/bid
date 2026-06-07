package com.xiyu.bid.approval.core;

import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;

import java.util.List;

/**
 * 审批动作状态机判定
 */
public class ApprovalDecisionPolicy {

    public ApprovalRuleResult canSubmit(List<ApprovalRequest> existingRequests) {
        boolean hasPending = existingRequests.stream()
                .anyMatch(request -> request.getStatus() == ApprovalStatus.PENDING);
        return hasPending
                ? ApprovalRuleResult.deny("该项目已有待审批的请求，请等待处理完成")
                : ApprovalRuleResult.allow();
    }

    public ApprovalRuleResult canApprove(ApprovalRequest request) {
        return request.canBeApproved()
                ? ApprovalRuleResult.allow()
                : ApprovalRuleResult.deny("当前状态不允许审批: " + request.getStatus().getDescription());
    }

    public ApprovalRuleResult canReject(ApprovalRequest request) {
        return request.canBeApproved()
                ? ApprovalRuleResult.allow()
                : ApprovalRuleResult.deny("当前状态不允许审批: " + request.getStatus().getDescription());
    }

    public ApprovalRuleResult canCancel(ApprovalRequest request, Long userId) {
        return request.canBeCancelledBy(userId)
                ? ApprovalRuleResult.allow()
                : ApprovalRuleResult.deny("只有申请人可以取消待审批的请求");
    }

    public ApprovalRuleResult canResubmit(ApprovalRequest request, Long userId) {
        if (request.getStatus() != ApprovalStatus.REJECTED) {
            return ApprovalRuleResult.deny("只有被驳回的请求可以重新提交");
        }
        if (!request.getRequesterId().equals(userId)) {
            return ApprovalRuleResult.deny("只有申请人可以重新提交");
        }
        return ApprovalRuleResult.allow();
    }

    public Long resolveApproverId(Long requestedApproverId, Long defaultApproverId) {
        return requestedApproverId != null ? requestedApproverId : defaultApproverId;
    }
}
