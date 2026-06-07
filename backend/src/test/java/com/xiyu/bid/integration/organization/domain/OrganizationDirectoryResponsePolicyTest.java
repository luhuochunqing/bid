package com.xiyu.bid.integration.organization.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationDirectoryResponsePolicy")
class OrganizationDirectoryResponsePolicyTest {

    @Test
    @DisplayName("success code with data is usable master data")
    void classify_successWithData_accepts() {
        OrganizationDirectoryResponseDecision decision = OrganizationDirectoryResponsePolicy.classify("200", true);

        assertThat(decision.outcome()).isEqualTo(OrganizationDirectoryResponseOutcome.SUCCESS);
        assertThat(decision.retryable()).isFalse();
    }

    @Test
    @DisplayName("success code without data is not found and must not be retried blindly")
    void classify_successWithoutData_isNotFound() {
        OrganizationDirectoryResponseDecision decision = OrganizationDirectoryResponsePolicy.classify("200", false);

        assertThat(decision.outcome()).isEqualTo(OrganizationDirectoryResponseOutcome.NOT_FOUND);
        assertThat(decision.retryable()).isFalse();
    }

    @Test
    @DisplayName("5xx style codes are retryable remote failures")
    void classify_remoteFailure_isRetryable() {
        OrganizationDirectoryResponseDecision decision = OrganizationDirectoryResponsePolicy.classify("503", false);

        assertThat(decision.outcome()).isEqualTo(OrganizationDirectoryResponseOutcome.RETRYABLE_FAILURE);
        assertThat(decision.retryable()).isTrue();
    }

    @Test
    @DisplayName("auth and parameter failures are non retryable contract failures")
    void classify_authFailure_isNonRetryable() {
        OrganizationDirectoryResponseDecision decision = OrganizationDirectoryResponsePolicy.classify("401", false);

        assertThat(decision.outcome()).isEqualTo(OrganizationDirectoryResponseOutcome.NON_RETRYABLE_FAILURE);
        assertThat(decision.retryable()).isFalse();
    }
}
