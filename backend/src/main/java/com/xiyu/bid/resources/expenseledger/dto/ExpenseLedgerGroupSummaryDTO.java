package com.xiyu.bid.resources.expenseledger.dto;

import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;

@Value
@Builder
public class ExpenseLedgerGroupSummaryDTO {
    String key;
    String label;
    long count;
    BigDecimal totalAmount;
}
