package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;

import java.util.Optional;

public class QualificationValidationPolicy {

    public Optional<String> validateValidityPeriod(ValidityPeriod period) {
        if (period == null) {
            return Optional.of("有效期不可为空");
        }
        if (period.getIssueDate() != null && period.getExpiryDate() != null) {
            if (period.getIssueDate().isAfter(period.getExpiryDate())) {
                return Optional.of("证书发证日期不可晚于到期日期");
            }
        }
        return Optional.empty();
    }
}

