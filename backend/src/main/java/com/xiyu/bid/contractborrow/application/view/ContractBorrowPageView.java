package com.xiyu.bid.contractborrow.application.view;

import java.util.List;

public record ContractBorrowPageView(
    List<ContractBorrowView> items,
    long total,
    int page,
    int size,
    int totalPages
) {
}
