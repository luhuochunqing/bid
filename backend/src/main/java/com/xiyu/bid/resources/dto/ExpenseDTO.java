package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class ExpenseDTO {
    Long id;
    Long projectId;
    ExpenseCategory category;
    String expenseType;
    BigDecimal amount;
    LocalDate date;
    String description;
    String createdBy;
    ExpenseStatus status;
    String approvalComment;
    String approvedBy;
    LocalDateTime approvedAt;
    LocalDateTime returnRequestedAt;
    LocalDateTime returnConfirmedAt;
    LocalDate expectedReturnDate;
    LocalDateTime lastReturnReminderAt;
    String returnComment;
    LocalDateTime paidAt;
    String paidBy;
    String paymentReference;
    String paymentMethod;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    public enum ExpenseCategory {
        MATERIAL,
        LABOR,
        EQUIPMENT,
        TRANSPORTATION,
        SUBCONTRACTING,
        OVERHEAD,
        OTHER
    }

    public enum ExpenseStatus {
        PENDING_APPROVAL,
        APPROVED,
        REJECTED,
        PAID,
        RETURN_REQUESTED,
        RETURNED
    }
}
