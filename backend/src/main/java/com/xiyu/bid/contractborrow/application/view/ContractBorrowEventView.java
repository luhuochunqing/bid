package com.xiyu.bid.contractborrow.application.view;

import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowEventType;
import com.xiyu.bid.contractborrow.domain.valueobject.ContractBorrowStatus;

import java.time.LocalDateTime;

public record ContractBorrowEventView(
    Long id,
    Long applicationId,
    ContractBorrowEventType eventType,
    ContractBorrowStatus statusAfter,
    String actorName,
    String comment,
    LocalDateTime createdAt
) {
}
