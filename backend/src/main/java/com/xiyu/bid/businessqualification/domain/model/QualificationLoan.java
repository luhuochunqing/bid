package com.xiyu.bid.businessqualification.domain.model;

import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record QualificationLoan(
        Long id,
        Long qualificationId,
        String borrower,
        String department,
        String projectId,
        String purpose,
        String remark,
        LocalDateTime borrowedAt,
        LocalDate expectedReturnDate,
        LocalDateTime returnedAt,
        String returnRemark,
        LoanStatus status
) {

    public QualificationLoan markReturned(LocalDateTime returnedAtValue, String returnRemarkValue) {
        return new QualificationLoan(
                id,
                qualificationId,
                borrower,
                department,
                projectId,
                purpose,
                remark,
                borrowedAt,
                expectedReturnDate,
                returnedAtValue,
                returnRemarkValue,
                LoanStatus.RETURNED
        );
    }

    public Long getId() {
        return id;
    }

    public Long getQualificationId() {
        return qualificationId;
    }

    public String getBorrower() {
        return borrower;
    }

    public String getDepartment() {
        return department;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getPurpose() {
        return purpose;
    }

    public String getRemark() {
        return remark;
    }

    public LocalDateTime getBorrowedAt() {
        return borrowedAt;
    }

    public LocalDate getExpectedReturnDate() {
        return expectedReturnDate;
    }

    public LocalDateTime getReturnedAt() {
        return returnedAt;
    }

    public String getReturnRemark() {
        return returnRemark;
    }

    public LoanStatus getStatus() {
        return status;
    }
}
