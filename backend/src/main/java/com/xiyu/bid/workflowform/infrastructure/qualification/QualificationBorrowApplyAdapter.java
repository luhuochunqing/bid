package com.xiyu.bid.workflowform.infrastructure.qualification;

import com.xiyu.bid.businessqualification.application.command.QualificationBorrowCommand;
import com.xiyu.bid.businessqualification.application.service.BorrowQualificationAppService;
import com.xiyu.bid.workflowform.application.port.QualificationBorrowApplyCommand;
import com.xiyu.bid.workflowform.application.port.QualificationBorrowApplyPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QualificationBorrowApplyAdapter implements QualificationBorrowApplyPort {

    private final BorrowQualificationAppService borrowQualificationAppService;

    @Override
    public void apply(QualificationBorrowApplyCommand command) {
        borrowQualificationAppService.borrow(command.qualificationId(), QualificationBorrowCommand.builder()
                .borrower(command.borrower())
                .department(command.department())
                .projectId(command.projectId() == null ? null : command.projectId().toString())
                .purpose(command.purpose())
                .expectedReturnDate(command.expectedReturnDate())
                .remark(command.remark())
                .build());
    }
}
