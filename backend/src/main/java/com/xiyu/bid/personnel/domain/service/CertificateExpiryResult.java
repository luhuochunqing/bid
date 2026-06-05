package com.xiyu.bid.personnel.domain.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;

import java.time.LocalDate;
import java.util.List;

public record CertificateExpiryResult(
        Personnel personnel,
        List<Certificate> expiringCertificates,
        int totalCertificates,
        int expiredCount,
        int expiringSoonCount
) {

    public boolean hasExpiring(int warningDays) {
        return expiringCertificates.stream().anyMatch(c -> c.isExpiringSoon(warningDays));
    }

    public boolean hasExpired() {
        return !expiringCertificates.isEmpty() &&
               expiringCertificates.stream().allMatch(Certificate::isExpired);
    }
}
