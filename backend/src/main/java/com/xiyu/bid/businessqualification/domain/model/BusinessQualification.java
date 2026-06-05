package com.xiyu.bid.businessqualification.domain.model;

import com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.LoanStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationCategory;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.QualificationSubject;
import com.xiyu.bid.businessqualification.domain.valueobject.ReminderPolicy;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record BusinessQualification(
        Long id,
        String name,
        QualificationSubject subject,
        QualificationCategory category,
        String certificateNo,
        String issuer,
        String holderName,
        ValidityPeriod validityPeriod,
        ReminderPolicy reminderPolicy,
        LoanStatus currentBorrowStatus,
        String currentBorrower,
        String currentDepartment,
        String currentProjectId,
        String borrowPurpose,
        LocalDate expectedReturnDate,
        String fileUrl,
        List<QualificationAttachment> attachments
) {

    public BusinessQualification {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static BusinessQualification create(
            Long id,
            String name,
            QualificationSubject subject,
            QualificationCategory category,
            String certificateNo,
            String issuer,
            String holderName,
            ValidityPeriod validityPeriod,
            ReminderPolicy reminderPolicy,
            LoanStatus currentBorrowStatus,
            String currentBorrower,
            String currentDepartment,
            String currentProjectId,
            String borrowPurpose,
            LocalDate expectedReturnDate,
            String fileUrl,
            List<QualificationAttachment> attachments
    ) {
        return new BusinessQualification(
                id,
                name,
                subject,
                category,
                certificateNo,
                issuer,
                holderName,
                validityPeriod,
                reminderPolicy,
                currentBorrowStatus,
                currentBorrower,
                currentDepartment,
                currentProjectId,
                borrowPurpose,
                expectedReturnDate,
                fileUrl,
                attachments
        );
    }

    public QualificationStatus status() {
        return new QualificationExpiryPolicy().evaluate(validityPeriod, LocalDate.now());
    }

    public long remainingDays() {
        return validityPeriod.remainingDays(LocalDate.now());
    }

    public BusinessQualification borrow(
            String borrower,
            String department,
            String projectId,
            String purpose,
            LocalDate expectedReturnDateValue
    ) {
        return copy(
                LoanStatus.BORROWED,
                borrower,
                department,
                projectId,
                purpose,
                expectedReturnDateValue,
                reminderPolicy
        );
    }

    public BusinessQualification returnBack() {
        return copy(
                LoanStatus.AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                reminderPolicy
        );
    }

    public BusinessQualification recordReminder(LocalDateTime remindedAt) {
        return copy(
                currentBorrowStatus,
                currentBorrower,
                currentDepartment,
                currentProjectId,
                borrowPurpose,
                expectedReturnDate,
                reminderPolicy.recordReminder(remindedAt)
        );
    }

    private BusinessQualification copy(
            LoanStatus nextBorrowStatus,
            String nextBorrower,
            String nextDepartment,
            String nextProjectId,
            String nextBorrowPurpose,
            LocalDate nextExpectedReturnDate,
            ReminderPolicy nextReminderPolicy
    ) {
        return new BusinessQualification(
                id,
                name,
                subject,
                category,
                certificateNo,
                issuer,
                holderName,
                validityPeriod,
                nextReminderPolicy,
                nextBorrowStatus,
                nextBorrower,
                nextDepartment,
                nextProjectId,
                nextBorrowPurpose,
                nextExpectedReturnDate,
                fileUrl,
                attachments
        );
    }
}
