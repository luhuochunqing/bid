package com.xiyu.bid.businessqualification.domain.service;

import com.xiyu.bid.businessqualification.domain.valueobject.ValidityPeriod;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class QualificationValidationPolicyTest {

    private final QualificationValidationPolicy policy = new QualificationValidationPolicy();

    @Test
    void should_return_error_when_period_is_null() {
        Optional<String> result = policy.validateValidityPeriod(null);
        assertThat(result).contains("有效期不可为空");
    }

    @Test
    void should_pass_when_dates_are_valid() {
        ValidityPeriod period = new ValidityPeriod(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 1));
        assertThat(policy.validateValidityPeriod(period)).isEmpty();
    }

    @Test
    void should_return_error_when_issue_date_after_expiry_date() {
        ValidityPeriod period = new ValidityPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 1, 1));
        Optional<String> result = policy.validateValidityPeriod(period);
        assertThat(result).contains("证书发证日期不可晚于到期日期");
    }

    @Test
    void should_pass_when_issue_date_is_null() {
        ValidityPeriod period = new ValidityPeriod(null, LocalDate.of(2026, 1, 1));
        assertThat(policy.validateValidityPeriod(period)).isEmpty();
    }

    @Test
    void should_pass_when_expiry_date_is_null() {
        ValidityPeriod period = new ValidityPeriod(LocalDate.of(2026, 1, 1), null);
        assertThat(policy.validateValidityPeriod(period)).isEmpty();
    }
}

