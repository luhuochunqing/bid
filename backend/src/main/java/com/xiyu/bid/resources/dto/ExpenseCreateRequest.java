package com.xiyu.bid.resources.dto;

import com.xiyu.bid.resources.entity.Expense;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ExpenseCreateRequest {

    @NotNull(message = "Project ID is required")
    private Long projectId;

    @NotNull(message = "Category is required")
    private Expense.ExpenseCategory category;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be positive")
    private BigDecimal amount;

    @NotNull(message = "Date is required")
    private LocalDate date;

    private LocalDate expectedReturnDate;

    private String expenseType;

    private String description;

    @NotNull(message = "Created by is required")
    private String createdBy;
}
