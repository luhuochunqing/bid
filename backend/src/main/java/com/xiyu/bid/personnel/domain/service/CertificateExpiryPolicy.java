package com.xiyu.bid.personnel.domain.service;

import com.xiyu.bid.personnel.domain.model.Personnel;
import com.xiyu.bid.personnel.domain.valueobject.Certificate;

import java.util.List;

public class CertificateExpiryPolicy {

    public List<Certificate> findExpiringSoon(List<Certificate> certificates, int warningDays) {
        return certificates.stream()
                .filter(c -> c.isExpiringSoon(warningDays))
                .toList();
    }

    public List<Certificate> findExpired(List<Certificate> certificates) {
        return certificates.stream()
                .filter(Certificate::isExpired)
                .toList();
    }

    public CertificateExpiryResult evaluate(Personnel personnel, int warningDays) {
        List<Certificate> certs = personnel.certificates();
        List<Certificate> expiring = findExpiringSoon(certs, warningDays);
        List<Certificate> expired = findExpired(certs);
        List<Certificate> allIssues = new java.util.ArrayList<>(expiring);
        expired.forEach(e -> { if (!allIssues.contains(e)) allIssues.add(e); });

        return new CertificateExpiryResult(
                personnel, allIssues, certs.size(),
                expired.size(),
                (int) expiring.stream().filter(c -> !c.isExpired()).count()
        );
    }
}
