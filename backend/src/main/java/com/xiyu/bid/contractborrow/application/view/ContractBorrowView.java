package com.xiyu.bid.contractborrow.application.view;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ContractBorrowView(
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
    ContractBorrowStatus status,
    String displayStatus,
    boolean overdue
) {
}
