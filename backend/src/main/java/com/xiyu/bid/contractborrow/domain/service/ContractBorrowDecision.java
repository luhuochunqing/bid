package com.xiyu.bid.contractborrow.domain.service;

import com.xiyu.bid.contractborrow.domain.model.ContractBorrowApplication;

public record ContractBorrowDecision(
    boolean allowed,
    ContractBorrowApplication application,
    String reason
) {
    public static ContractBorrowDecision allow(ContractBorrowApplication application) {
        return new ContractBorrowDecision(true, application, null);
    }

    public static ContractBorrowDecision deny(ContractBorrowApplication application, String reason) {
        return new ContractBorrowDecision(false, application, reason);
    }
}
