package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class ExpenseApprovalRecordDTO {
    Long id;
    Long expenseId;
    ApprovalResult result;
    String comment;
    String approver;
    LocalDateTime actedAt;

    public enum ApprovalResult {
        APPROVED,
        REJECTED
    }
}
