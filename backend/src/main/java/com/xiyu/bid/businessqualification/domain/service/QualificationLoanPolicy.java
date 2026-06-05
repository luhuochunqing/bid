package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.model.BusinessQualification;
import com.xiyu.bid.businessqualification.domain.model.QualificationLoan;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import org.springframework.stereotype.Component;

@Component
public class QualificationLoanPolicy {

    public QualificationValidationResult validateBorrow(BusinessQualification qualification) {
        if (qualification.currentBorrowStatus() == LoanStatus.BORROWED) {
            return QualificationValidationResult.invalid("该资质当前已借出，不能重复借阅");
        }
        if (qualification.status() == QualificationStatus.EXPIRED) {
            return QualificationValidationResult.invalid("已过期资质不能借阅");
        }
        return QualificationValidationResult.success();
    }

    public QualificationValidationResult validateReturn(
            BusinessQualification qualification,
            QualificationLoan activeLoan
    ) {
        if (qualification.currentBorrowStatus() != LoanStatus.BORROWED
                || activeLoan == null
                || activeLoan.status() != LoanStatus.BORROWED) {
            return QualificationValidationResult.invalid("该资质当前没有活动借阅记录");
        }
        return QualificationValidationResult.success();
    }
}
