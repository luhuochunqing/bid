package com.xiyu.bid.resources.expenseledger.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.util.List;

@Value
@Builder
public class ExpenseLedgerSummaryDTO {
    long recordCount;
    BigDecimal totalAmount;
    BigDecimal pendingApprovalAmount;
    BigDecimal approvedAmount;
    BigDecimal paidAmount;
    BigDecimal returnRequestedAmount;
    BigDecimal returnedAmount;
    long depositCount;
    long pendingReturnCount;
    List<ExpenseLedgerGroupSummaryDTO> byDepartment;
    List<ExpenseLedgerGroupSummaryDTO> byProject;
    List<ExpenseLedgerGroupSummaryDTO> byExpenseType;
    List<ExpenseLedgerGroupSummaryDTO> byStatus;
}
