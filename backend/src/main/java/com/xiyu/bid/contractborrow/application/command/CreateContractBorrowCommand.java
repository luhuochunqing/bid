package com.xiyu.bid.contractborrow.application.command;

import java.time.LocalDate;

public record CreateContractBorrowCommand(
    String contractNo,
    String contractName,
    String sourceName,
    String borrowerName,
    String borrowerDept,
    String customerName,
    String purpose,
    String borrowType,
    LocalDate expectedReturnDate
) {
}
