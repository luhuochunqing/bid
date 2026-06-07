package com.xiyu.bid.personnel.domain.valueobject;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public record Certificate(
        Long id,
        String name,
        String certificateNumber,
        CertificateType type,
        LocalDate issueDate,
        LocalDate expiryDate,
        String attachmentUrl
) {

    public boolean isExpired() {
        return expiryDate != null && !expiryDate.isAfter(LocalDate.now());
    }

    public boolean isExpiringSoon(int warningDays) {
        if (expiryDate == null) return false;
        long remaining = ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        return remaining > 0 && remaining <= warningDays;
    }

    public long remainingDays() {
        if (expiryDate == null) return Long.MAX_VALUE;
        return Math.max(0, ChronoUnit.DAYS.between(LocalDate.now(), expiryDate));
    }
}
