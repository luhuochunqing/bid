package com.xiyu.bid.workflowform.application.port;

import java.time.LocalDate;

public record QualificationBorrowApplyCommand(
        Long qualificationId,
        String borrower,
        String department,
        Long projectId,
        String purpose,
        LocalDate expectedReturnDate,
        String remark
) {
}
