package com.xiyu.bid.approval.service;

import com.xiyu.bid.approval.entity.ApprovalRequest;
import com.xiyu.bid.approval.enums.ApprovalStatus;

import java.util.List;

final class ApprovalDecisionPolicy {

    private ApprovalDecisionPolicy() {
    }

    static Decision canSubmit(List<ApprovalRequest> existingRequests) {
        boolean hasPending = existingRequests.stream().anyMatch(request -> request.getStatus() == ApprovalStatus.PENDING);
        return hasPending
                ? Decision.rejected("该项目已有待审批的请求，请等待处理完成")
                : Decision.allowed();
    }

    static Decision canApprove(ApprovalRequest request) {
        return request.canBeApproved()
                ? Decision.allowed()
                : Decision.rejected("当前状态不允许审批: " + request.getStatus().getDescription());
    }

    static Decision canCancel(ApprovalRequest request, Long userId) {
        return request.canBeCancelledBy(userId)
                ? Decision.allowed()
                : Decision.rejected("只有申请人可以取消待审批的请求");
    }

    static Decision canResubmit(ApprovalRequest request, Long userId) {
        if (request.getStatus() != ApprovalStatus.REJECTED) {
            return Decision.rejected("只有被驳回的请求可以重新提交");
        }
        if (!request.getRequesterId().equals(userId)) {
            return Decision.rejected("只有申请人可以重新提交");
        }
        return Decision.allowed();
    }

    record Decision(boolean permitted, String message) {
        static Decision allowed() {
            return new Decision(true, null);
        }

        static Decision rejected(String message) {
            return new Decision(false, message);
        }
    }
}
