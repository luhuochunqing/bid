package com.xiyu.bid.resources.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Value
@Builder
public class ExpensePaymentRecordDTO {
    Long id;
    Long expenseId;
    BigDecimal amount;
    LocalDateTime paidAt;
    String paidBy;
    String paymentReference;
    String paymentMethod;
    String remark;
    LocalDateTime createdAt;
}
