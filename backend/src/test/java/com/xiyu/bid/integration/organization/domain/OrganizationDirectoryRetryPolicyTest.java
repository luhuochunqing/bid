package com.xiyu.bid.integration.organization.domain;

import com.xiyu.bid.platform.async.application.AsyncDecisionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationDirectoryRetryPolicy")
class OrganizationDirectoryRetryPolicyTest {

    private final OrganizationDirectoryRetryPolicy policy = new OrganizationDirectoryRetryPolicy(new AsyncDecisionResolver());

    @Test
    @DisplayName("schedules exponential backoff with a safe cap")
    void decide_retryable_schedulesBackoffWithCap() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");

        OrganizationRetryDecision decision = policy.decide(2, 5, now);

        assertThat(decision.retryable()).isTrue();
        assertThat(decision.status()).isEqualTo(OrganizationEventStatus.PENDING_RETRY);
        assertThat(decision.nextRetryAt()).isEqualTo(now.plusMinutes(20));
    }

    @Test
    @DisplayName("marks dead letter when attempts are exhausted")
    void decide_attemptsExhausted_marksDeadLetter() {
        OrganizationRetryDecision decision = policy.decide(
                5,
                5,
                LocalDateTime.parse("2026-05-15T10:00:00")
        );

        assertThat(decision.retryable()).isFalse();
        assertThat(decision.status()).isEqualTo(OrganizationEventStatus.DEAD_LETTER);
        assertThat(decision.nextRetryAt()).isNull();
    }
}
