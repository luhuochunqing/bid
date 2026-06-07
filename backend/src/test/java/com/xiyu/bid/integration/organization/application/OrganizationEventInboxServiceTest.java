package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventNotice;
import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.domain.OrganizationEventType;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationEventLogEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationEventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationEventInboxService - idempotency and status")
class OrganizationEventInboxServiceTest {
    @Mock
    private OrganizationEventLogRepository repository;

    @Test
    @DisplayName("reserve stores notice identity and raw payload")
    void reserve_storesNoticeIdentity() {
        when(repository.saveAndFlush(any(OrganizationEventLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationEventInboxService service = service();

        boolean reserved = service.reserve(notice(), "{\"data\":{\"userId\":\"10001\"}}");

        assertThat(reserved).isTrue();
        ArgumentCaptor<OrganizationEventLogEntity> saved = ArgumentCaptor.forClass(OrganizationEventLogEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventTopic()).isEqualTo("BaseOssUser");
        assertThat(saved.getValue().getExternalUserId()).isEqualTo("10001");
        assertThat(saved.getValue().getStatus()).isEqualTo(OrganizationEventStatus.PROCESSING);
    }

    @Test
    @DisplayName("reserve stores millisecond event time from event bus")
    void reserve_storesEpochMillisEventTime() {
        when(repository.saveAndFlush(any(OrganizationEventLogEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        OrganizationEventInboxService service = service();

        service.reserve(new OrganizationEventNotice(
                "trace-1", "span-1", "", "oss",
                OrganizationEventType.USER_NOTICE, "1730884403101", "720518523", "720518523"
        ), "{}");

        ArgumentCaptor<OrganizationEventLogEntity> saved = ArgumentCaptor.forClass(OrganizationEventLogEntity.class);
        verify(repository).saveAndFlush(saved.capture());
        assertThat(saved.getValue().getEventTime()).isNotNull();
    }

    @Test
    @DisplayName("duplicate unique key returns false")
    void reserve_duplicate_returnsFalse() {
        when(repository.saveAndFlush(any(OrganizationEventLogEntity.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));
        OrganizationEventInboxService service = service();

        assertThat(service.reserve(notice(), "{}")).isFalse();
    }

    @Test
    @DisplayName("retryable failure increments retry metadata")
    void markFailed_updatesRetryFields() {
        OrganizationEventLogEntity existing = new OrganizationEventLogEntity();
        existing.setEventKey("event-key");
        existing.setRetryCount(0);
        when(repository.findByEventKey("event-key")).thenReturn(Optional.of(existing));
        OrganizationEventInboxService service = service();

        service.markFailed("event-key", "接口超时", "TIMEOUT");

        assertThat(existing.getStatus()).isEqualTo(OrganizationEventStatus.PENDING_RETRY);
        assertThat(existing.getRetryCount()).isEqualTo(1);
        assertThat(existing.getLastErrorCode()).isEqualTo("TRANSIENT_DEPENDENCY");
        assertThat(existing.getNextRetryAt()).isNotNull();
    }

    @Test
    @DisplayName("exhausted failure becomes dead letter")
    void markRetryableFailure_attemptsExhausted_marksDeadLetter() {
        OrganizationEventLogEntity existing = new OrganizationEventLogEntity();
        existing.setEventKey("event-key");
        existing.setRetryCount(4);
        when(repository.findByEventKey("event-key")).thenReturn(Optional.of(existing));
        OrganizationEventInboxService service = service();

        service.markRetryableFailure("event-key", "接口超时", "TIMEOUT", 5);

        assertThat(existing.getStatus()).isEqualTo(OrganizationEventStatus.DEAD_LETTER);
        assertThat(existing.getRetryCount()).isEqualTo(5);
        assertThat(existing.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("mark failed honors configured max retry attempts")
    void markFailed_usesConfiguredMaxAttempts() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.getRetry().setMaxAttempts(2);
        OrganizationEventLogEntity existing = new OrganizationEventLogEntity();
        existing.setEventKey("event-key");
        existing.setRetryCount(1);
        when(repository.findByEventKey("event-key")).thenReturn(Optional.of(existing));

        new ApplicationContextRunner()
                .withBean(OrganizationEventLogRepository.class, () -> repository)
                .withBean(OrganizationIntegrationProperties.class, () -> properties)
                .withBean(com.xiyu.bid.integration.organization.domain.OrganizationDirectoryRetryPolicy.class,
                        () -> new com.xiyu.bid.integration.organization.domain.OrganizationDirectoryRetryPolicy(
                                new com.xiyu.bid.platform.async.application.AsyncDecisionResolver()))
                .withBean(com.xiyu.bid.metrics.OrgSyncMetrics.class,
                        () -> new com.xiyu.bid.metrics.OrgSyncMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry()))
                .withUserConfiguration(OrganizationEventInboxService.class)
                .run(context -> context.getBean(OrganizationEventInboxService.class)
                        .markFailed("event-key", "接口超时", "TIMEOUT"));

        assertThat(existing.getStatus()).isEqualTo(OrganizationEventStatus.DEAD_LETTER);
        assertThat(existing.getRetryCount()).isEqualTo(2);
        assertThat(existing.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("non retryable failure keeps event metadata and moves to dead letter")
    void markNonRetryableFailure_preservesMetadata() {
        OrganizationEventLogEntity existing = new OrganizationEventLogEntity();
        existing.setEventKey("event-key");
        existing.setEventTopic("BaseOssUser");
        existing.setSourceApp("customer-org");
        existing.setTraceId("trace-1");
        existing.setNextRetryAt(java.time.LocalDateTime.now());
        when(repository.findByEventKey("event-key")).thenReturn(Optional.of(existing));
        OrganizationEventInboxService service = service();

        service.markNonRetryableFailure("event-key", "鉴权失败", "DIRECTORY_GATEWAY_NON_RETRYABLE");

        assertThat(existing.getStatus()).isEqualTo(OrganizationEventStatus.DEAD_LETTER);
        assertThat(existing.getEventTopic()).isEqualTo("BaseOssUser");
        assertThat(existing.getSourceApp()).isEqualTo("customer-org");
        assertThat(existing.getNextRetryAt()).isNull();
    }

    @Test
    @DisplayName("claims due retry with atomic repository update")
    void claimDueRetry_usesRepositoryClaim() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        when(repository.claimDueRetry(
                "event-key",
                OrganizationEventStatus.PENDING_RETRY,
                OrganizationEventStatus.PROCESSING,
                now,
                "retrying"
        )).thenReturn(1);
        OrganizationEventInboxService service = service();

        assertThat(service.claimDueRetry("event-key", now)).isTrue();
    }

    @Test
    @DisplayName("claims dead letter replay with atomic repository update")
    void claimDeadLetterReplay_usesRepositoryClaim() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        when(repository.claimDeadLetterReplay(
                "event-key",
                OrganizationEventStatus.DEAD_LETTER,
                OrganizationEventStatus.PROCESSING,
                now,
                "manual replaying"
        )).thenReturn(1);
        OrganizationEventInboxService service = service();

        assertThat(service.claimDeadLetterReplay("event-key", now)).isTrue();
    }

    @Test
    @DisplayName("stale processing recovery keeps retry and manual replay leases separate")
    void recoverStaleProcessing_recoversRetryAndManualReplaySeparately() {
        LocalDateTime cutoff = LocalDateTime.parse("2026-05-15T09:45:00");
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        when(repository.recoverStaleProcessing(
                OrganizationEventStatus.PROCESSING,
                OrganizationEventStatus.PENDING_RETRY,
                cutoff,
                now,
                "processing lease expired",
                "manual replaying"
        )).thenReturn(2);
        when(repository.recoverStaleDeadLetterReplay(
                OrganizationEventStatus.PROCESSING,
                OrganizationEventStatus.DEAD_LETTER,
                cutoff,
                now,
                "manual replaying",
                "manual replay lease expired"
        )).thenReturn(1);
        OrganizationEventInboxService service = service();

        assertThat(service.recoverStaleProcessing(cutoff, now)).isEqualTo(3);
    }

    private OrganizationEventInboxService service() {
        return new OrganizationEventInboxService(
                repository,
                new OrganizationIntegrationProperties(),
                new com.xiyu.bid.integration.organization.domain.OrganizationDirectoryRetryPolicy(new com.xiyu.bid.platform.async.application.AsyncDecisionResolver()),
                new com.xiyu.bid.metrics.OrgSyncMetrics(new io.micrometer.core.instrument.simple.SimpleMeterRegistry())
        );
    }

    private OrganizationEventNotice notice() {
        return new OrganizationEventNotice(
                "trace-1", "span-1", "parent-1", "customer-org",
                OrganizationEventType.USER_NOTICE, "2026-04-30T10:15:30+08:00", "event-1", "10001"
        );
    }
}
