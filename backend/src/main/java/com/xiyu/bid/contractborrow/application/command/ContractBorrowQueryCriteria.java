package com.xiyu.bid.contractborrow.application.command;

public record ContractBorrowQueryCriteria(
    String keyword,
    String status,
    String borrowerName
) {
}
