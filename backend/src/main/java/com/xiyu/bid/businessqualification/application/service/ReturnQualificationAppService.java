package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.application.command.QualificationReturnCommand;
import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.port.BusinessQualificationRepository;
import com.xiyu.bid.businessqualification.domain.port.QualificationLoanRecordRepository;
import com.xiyu.bid.businessqualification.domain.service.QualificationLoanPolicy;
import com.xiyu.bid.businessqualification.domain.service.QualificationValidationResult;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ReturnQualificationAppService {

    private final BusinessQualificationRepository qualificationRepository;
    private final QualificationLoanRecordRepository loanRecordRepository;
    private final QualificationLoanPolicy loanPolicy;

    @Transactional
    public QualificationLoan returnLoan(Long qualificationId, QualificationReturnCommand command) {
        BusinessQualification qualification = qualificationRepository.findById(qualificationId)
                .orElseThrow(() -> new ResourceNotFoundException("BusinessQualification", String.valueOf(qualificationId)));
        QualificationLoan activeLoan = loanRecordRepository.findActiveByQualificationId(qualificationId)
                .orElseThrow(() -> new ResourceNotFoundException("QualificationLoan", String.valueOf(qualificationId)));
        return returnLoan(qualification, activeLoan, command);
    }

    @Transactional
    public QualificationLoan returnLoanByRecordId(Long recordId, QualificationReturnCommand command) {
        QualificationLoan loan = loanRecordRepository.findById(recordId)
                .orElseThrow(() -> new ResourceNotFoundException("QualificationLoan", String.valueOf(recordId)));
        BusinessQualification qualification = qualificationRepository.findById(loan.getQualificationId())
                .orElseThrow(() -> new ResourceNotFoundException("BusinessQualification", String.valueOf(loan.getQualificationId())));
        return returnLoan(qualification, loan, command);
    }

    private QualificationLoan returnLoan(
            BusinessQualification qualification,
            QualificationLoan activeLoan,
            QualificationReturnCommand command
    ) {
        requireValid(loanPolicy.validateReturn(qualification, activeLoan));

        qualificationRepository.save(qualification.returnBack());

        QualificationLoan returnedLoan = activeLoan.markReturned(LocalDateTime.now(), command.getReturnRemark());
        return loanRecordRepository.save(returnedLoan);
    }

    private void requireValid(QualificationValidationResult validationResult) {
        if (!validationResult.valid()) {
            throw new IllegalArgumentException(validationResult.message());
        }
    }
}
