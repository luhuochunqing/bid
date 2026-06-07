package com.xiyu.bid.contractborrow.domain.service;

import com.xiyu.bid.contractborrow.domain.model.ContractBorrowApplication;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;

import java.time.LocalDateTime;

public final class ContractBorrowLifecyclePolicy {

    private ContractBorrowLifecyclePolicy() {
    }

    public static ContractBorrowDecision approve(
        ContractBorrowApplication application,
        LocalDateTime approvedAt,
        String approverName
    ) {
        if (application.status() != ContractBorrowStatus.PENDING_APPROVAL) {
            return ContractBorrowDecision.deny(application, "只有待审批的合同借阅申请可以审批");
        }
        return ContractBorrowDecision.allow(application.withStatus(
            ContractBorrowStatus.APPROVED,
            approverName,
            approvedAt,
            application.rejectionReason(),
            application.rejectedAt(),
            application.returnRemark(),
            application.returnedAt(),
            application.cancelReason(),
            application.cancelledAt(),
            approverName
        ));
    }

    public static ContractBorrowDecision reject(
        ContractBorrowApplication application,
        LocalDateTime rejectedAt,
        String reason
    ) {
        if (application.status() != ContractBorrowStatus.PENDING_APPROVAL) {
            return ContractBorrowDecision.deny(application, "只有待审批的合同借阅申请可以驳回");
        }
        return ContractBorrowDecision.allow(application.withStatus(
            ContractBorrowStatus.REJECTED,
            application.approverName(),
            application.approvedAt(),
            reason,
            rejectedAt,
            application.returnRemark(),
            application.returnedAt(),
            application.cancelReason(),
            application.cancelledAt(),
            reason
        ));
    }

    public static ContractBorrowDecision returnBack(
        ContractBorrowApplication application,
        LocalDateTime returnedAt,
        String remark
    ) {
        if (application.status() != ContractBorrowStatus.APPROVED
            && application.status() != ContractBorrowStatus.BORROWED) {
            return ContractBorrowDecision.deny(application, "只有已审批或借出中的合同借阅申请可以归还");
        }
        return ContractBorrowDecision.allow(application.withStatus(
            ContractBorrowStatus.RETURNED,
            application.approverName(),
            application.approvedAt(),
            application.rejectionReason(),
            application.rejectedAt(),
            remark,
            returnedAt,
            application.cancelReason(),
            application.cancelledAt(),
            remark
        ));
    }

    public static ContractBorrowDecision cancel(
        ContractBorrowApplication application,
        LocalDateTime cancelledAt,
        String reason
    ) {
        if (application.status() != ContractBorrowStatus.PENDING_APPROVAL) {
            return ContractBorrowDecision.deny(application, "只有待审批的合同借阅申请可以取消");
        }
        return ContractBorrowDecision.allow(application.withStatus(
            ContractBorrowStatus.CANCELLED,
            application.approverName(),
            application.approvedAt(),
            application.rejectionReason(),
            application.rejectedAt(),
            application.returnRemark(),
            application.returnedAt(),
            reason,
            cancelledAt,
            reason
        ));
    }
}
