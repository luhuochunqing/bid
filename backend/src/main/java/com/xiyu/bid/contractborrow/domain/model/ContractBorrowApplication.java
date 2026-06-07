package com.xiyu.bid.contractborrow.domain.model;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

public record ContractBorrowApplication(
    Long id,
    String contractNo,
    String contractName,
    String sourceName,
    String borrowerName,
    String borrowerDept,
    String customerName,
    String purpose,
    String borrowType,
    LocalDate expectedReturnDate,
    LocalDateTime submittedAt,
    String approverName,
    LocalDateTime approvedAt,
    String rejectionReason,
    LocalDateTime rejectedAt,
    String returnRemark,
    LocalDateTime returnedAt,
    String cancelReason,
    LocalDateTime cancelledAt,
    String lastComment,
    ContractBorrowStatus status
) {
    private static final Set<ContractBorrowStatus> ACTIVE_STATUSES = Set.of(
        ContractBorrowStatus.APPROVED,
        ContractBorrowStatus.BORROWED
    );

    public ContractBorrowApplication withExpectedReturnDate(LocalDate newExpectedReturnDate) {
        return new ContractBorrowApplication(
            id,
            contractNo,
            contractName,
            sourceName,
            borrowerName,
            borrowerDept,
            customerName,
            purpose,
            borrowType,
            newExpectedReturnDate,
            submittedAt,
            approverName,
            approvedAt,
            rejectionReason,
            rejectedAt,
            returnRemark,
            returnedAt,
            cancelReason,
            cancelledAt,
            lastComment,
            status
        );
    }

    public ContractBorrowApplication withStatus(
        ContractBorrowStatus newStatus,
        String newApproverName,
        LocalDateTime newApprovedAt,
        String newRejectionReason,
        LocalDateTime newRejectedAt,
        String newReturnRemark,
        LocalDateTime newReturnedAt,
        String newCancelReason,
        LocalDateTime newCancelledAt,
        String newLastComment
    ) {
        return new ContractBorrowApplication(
            id,
            contractNo,
            contractName,
            sourceName,
            borrowerName,
            borrowerDept,
            customerName,
            purpose,
            borrowType,
            expectedReturnDate,
            submittedAt,
            newApproverName,
            newApprovedAt,
            newRejectionReason,
            newRejectedAt,
            newReturnRemark,
            newReturnedAt,
            newCancelReason,
            newCancelledAt,
            newLastComment,
            newStatus
        );
    }

    public boolean isOverdue(LocalDate today) {
        return expectedReturnDate != null
            && today != null
            && ACTIVE_STATUSES.contains(status)
            && expectedReturnDate.isBefore(today);
    }

    public String displayStatus(LocalDate today) {
        if (isOverdue(today)) {
            return "OVERDUE";
        }
        return status.name();
    }
}
