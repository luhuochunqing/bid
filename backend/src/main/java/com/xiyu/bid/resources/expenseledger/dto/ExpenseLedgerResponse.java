package com.xiyu.bid.resources.expenseledger.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class ExpenseLedgerResponse {
    List<ExpenseLedgerItemDTO> items;
    ExpenseLedgerSummaryDTO summary;
}
