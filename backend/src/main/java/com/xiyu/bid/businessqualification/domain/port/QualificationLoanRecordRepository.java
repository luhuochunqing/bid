package com.xiyu.bid.businessqualification.domain.port;

import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;

import java.util.List;
import java.util.Optional;

public interface QualificationLoanRecordRepository {
    QualificationLoan save(QualificationLoan loan);

    Optional<QualificationLoan> findById(Long id);

    Optional<QualificationLoan> findActiveByQualificationId(Long qualificationId);

    List<QualificationLoan> findByQualificationId(Long qualificationId);

    List<QualificationLoan> findAllOrderByBorrowedAtDesc();
}
