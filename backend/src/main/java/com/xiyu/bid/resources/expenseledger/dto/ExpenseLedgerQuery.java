package com.xiyu.bid.resources.expenseledger.dto;

import com.xiyu.bid.resources.dto.ExpenseDTO;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class ExpenseLedgerQuery {
    Long projectId;
    String projectKeyword;
    LocalDate startDate;
    LocalDate endDate;
    String department;
    String expenseType;
    ExpenseDTO.ExpenseStatus status;
}
