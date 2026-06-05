package com.xiyu.bid.resources.dto;

import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseUpdateRequest {

    private com.xiyu.bid.resources.entity.Expense.ExpenseCategory category;

    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    private LocalDate date;

    private LocalDate expectedReturnDate;

    private String expenseType;

    private String description;
}
