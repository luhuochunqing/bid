package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationBorrowCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.port.QualificationLoanRecordRepository;
import com.xiyu.bid.businessqualification.domain.service.QualificationLoanPolicy;
import com.xiyu.bid.businessqualification.domain.service.QualificationValidationResult;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class BorrowQualificationAppService {

    private final BusinessQualificationRepository qualificationRepository;
    private final QualificationLoanRecordRepository loanRecordRepository;
    private final QualificationLoanPolicy loanPolicy;

    @Transactional
    public QualificationLoan borrow(Long qualificationId, QualificationBorrowCommand command) {
        BusinessQualification qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessQualification", String.valueOf(qualificationId)));
        requireValid(loanPolicy.validateBorrow(qualification));

        BusinessQualification borrowedQualification = qualification.borrow(
                command.getBorrower(),
                command.getDepartment(),
                command.getProjectId(),
                command.getPurpose(),
                command.getExpectedReturnDate());

        qualificationRepository.save(borrowedQualification);

        QualificationLoan loan = new QualificationLoan(
                null,
                qualificationId,
                command.getBorrower(),
                command.getDepartment(),
                command.getProjectId(),
                command.getPurpose(),
                command.getRemark(),
                LocalDateTime.now(),
                command.getExpectedReturnDate(),
                null,
                null,
                LoanStatus.BORROWED
        );
        return loanRecordRepository.save(loan);
    }

    private void requireValid(QualificationValidationResult validationResult) {
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.message());
        }
    }
}
