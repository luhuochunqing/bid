package com.xiyu.bid.contractborrow.application.view;

public record ContractBorrowOverviewView(
    long total,
    long pendingApproval,
    long approved,
    long borrowed,
    long returned,
    long rejected,
    long cancelled,
    long overdue
) {
}
