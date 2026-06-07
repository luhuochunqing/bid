package com.xiyu.bid.businessqualification.application.service;

import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.port.QualificationLoanRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetQualificationBorrowRecordsAppService {

    private final QualificationLoanRecordRepository loanRecordRepository;

    @Transactional(readOnly = true)
    public List<QualificationLoan> getBorrowRecords(Long qualificationId) {
        return loanRecordRepository.findByQualificationId(qualificationId);
    }

    @Transactional(readOnly = true)
    public List<QualificationLoan> getBorrowRecords() {
        return loanRecordRepository.findAllOrderByBorrowedAtDesc();
    }
}
