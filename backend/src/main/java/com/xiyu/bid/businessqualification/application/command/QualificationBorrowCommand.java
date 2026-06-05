package com.xiyu.bid.businessqualification.application.command;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDate;

@Value
@Builder
public class QualificationBorrowCommand {
    String borrower;
    String department;
    String projectId;
    String purpose;
    LocalDate expectedReturnDate;
    String remark;
}
