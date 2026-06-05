package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.valueobject.QualificationStatus;
import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

@Component
public class QualificationExpiryPolicy {

    private static final int DEFAULT_EXPIRING_DAYS = 30;

    public QualificationStatus evaluate(ValidityPeriod period, LocalDate today) {
        long remainingDays = period.remainingDays(today);
        if (remainingDays < 0) {
            return QualificationStatus.EXPIRED;
        }
        if (remainingDays <= DEFAULT_EXPIRING_DAYS) {
            return QualificationStatus.EXPIRING;
        }
        return QualificationStatus.VALID;
    }

    public String alertLevel(QualificationStatus status) {
        if (status == QualificationStatus.EXPIRED) {
            return "danger";
        }
        if (status == QualificationStatus.EXPIRING) {
            return "warning";
        }
        return "none";
    }
}
