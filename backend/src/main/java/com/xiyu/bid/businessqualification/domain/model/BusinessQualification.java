package com.xiyu.bid.businessqualification.domain.model;

import com.xiyu.bid.businessqualification.domain.service.QualificationExpiryPolicy;
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
        String level,
        QualificationSubject subject,
        QualificationCategory category,
        String certificateNo,
        String issuer,
        String agency,
        String agencyContact,
        String certScope,
        String certReviewNote,
        String holderName,
        ValidityPeriod validityPeriod,
        ReminderPolicy reminderPolicy,
        String fileUrl,
        String retireReason,
        boolean retired,
        List<QualificationAttachment> attachments
) {

    public BusinessQualification {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }

    public static BusinessQualification create(
            Long id,
            String name,
            String level,
            QualificationSubject subject,
            QualificationCategory category,
            String certificateNo,
            String issuer,
            String agency,
            String agencyContact,
            String certScope,
            String certReviewNote,
            String holderName,
            ValidityPeriod validityPeriod,
            ReminderPolicy reminderPolicy,
            String fileUrl,
            String retireReason,
            List<QualificationAttachment> attachments
    ) {
        return new BusinessQualification(
                id,
                name,
                level,
                subject,
                category,
                certificateNo,
                issuer,
                agency,
                agencyContact,
                certScope,
                certReviewNote,
                holderName,
                validityPeriod,
                reminderPolicy,
                fileUrl,
                retireReason,
                false,
                attachments
        );
    }

    public static BusinessQualification createWithRetired(
            Long id,
            String name,
            String level,
            QualificationSubject subject,
            QualificationCategory category,
            String certificateNo,
            String issuer,
            String agency,
            String agencyContact,
            String certScope,
            String certReviewNote,
            String holderName,
            ValidityPeriod validityPeriod,
            ReminderPolicy reminderPolicy,
            String fileUrl,
            String retireReason,
            boolean retired,
            List<QualificationAttachment> attachments
    ) {
        return new BusinessQualification(
                id,
                name,
                level,
                subject,
                category,
                certificateNo,
                issuer,
                agency,
                agencyContact,
                certScope,
                certReviewNote,
                holderName,
                validityPeriod,
                reminderPolicy,
                fileUrl,
                retireReason,
                retired,
                attachments
        );
    }

    public QualificationStatus status() {
        if (retired) return QualificationStatus.RETIRED;
        return new QualificationExpiryPolicy().evaluate(validityPeriod, LocalDate.now());
    }

    public long remainingDays() {
        return validityPeriod.remainingDays(LocalDate.now());
    }



    public BusinessQualification recordReminder(LocalDateTime remindedAt) {
        return copy(reminderPolicy.recordReminder(remindedAt)
        );
    }

    private BusinessQualification copy(ReminderPolicy nextReminderPolicy) {
        return new BusinessQualification(
                id,
                name,
                level,
                subject,
                category,
                certificateNo,
                issuer,
                agency,
                agencyContact,
                certScope,
                certReviewNote,
                holderName,
                validityPeriod,
                nextReminderPolicy,
                fileUrl,
                retireReason,
                retired,
                attachments
        );
    }
}
