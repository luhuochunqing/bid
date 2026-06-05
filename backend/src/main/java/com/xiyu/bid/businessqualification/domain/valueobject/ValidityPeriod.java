package com.xiyu.bid.businessqualification.domain.valueobject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record ValidityPeriod(
        LocalDate issueDate,
        LocalDate expiryDate
) {

    public long remainingDays(LocalDate today) {
        if (expiryDate == null) {
            return Long.MAX_VALUE;
        }
        return ChronoUnit.DAYS.between(today, expiryDate);
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}
