package com.xiyu.bid.resources.expenseledger.dto;

import com.xiyu.bid.resources.dto.ExpenseDTO;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Value
@Builder
public class ExpenseLedgerItemDTO {
    Long id;
    Long projectId;
    String projectName;
    String departmentCode;
    String departmentName;
    ExpenseDTO.ExpenseCategory category;
    String expenseType;
    BigDecimal amount;
    LocalDate date;
    String description;
    String createdBy;
    ExpenseDTO.ExpenseStatus status;
    String approvalComment;
    String approvedBy;
    LocalDateTime approvedAt;
    LocalDateTime returnRequestedAt;
    LocalDateTime returnConfirmedAt;
    String returnComment;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;
}
