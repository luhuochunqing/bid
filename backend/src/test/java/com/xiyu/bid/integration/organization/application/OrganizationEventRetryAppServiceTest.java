package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookData;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationEventLogEntity;
import com.xiyu.bid.platform.async.infrastructure.AsyncObservabilityRecorder;
import com.xiyu.bid.support.NoOpAsyncObservabilityTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationEventRetryAppService")
class OrganizationEventRetryAppServiceTest {
    @Mock
    private OrganizationEventInboxService inboxService;
    @Mock
    private OrganizationDirectorySyncAppService syncAppService;
    @Mock
    private AsyncObservabilityRecorder observabilityRecorder;

    @Test
    @DisplayName("retries due pending events without reserving them as new events")
    void retryDueEvents_reprocessesDuePayload() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationEventLogEntity event = eventLog("event-key", "{}");
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.getRetry().setBatchSize(50);
        when(inboxService.findDueRetries(now, 50)).thenReturn(List.of(event));
        when(inboxService.claimDueRetry("event-key", now)).thenReturn(true);
        when(syncAppService.reprocessReservedEvent("event-key", "{}")).thenReturn(response("200"));

        OrganizationEventRetrySummary summary = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                properties,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true),
                observabilityRecorder
        ).retryDueEvents(now);

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.successCount()).isEqualTo(1);
        assertThat(summary.failedCount()).isZero();
        verify(inboxService).recoverStaleProcessing(now.minusMinutes(15), now);
        verify(inboxService).claimDueRetry("event-key", now);
        verify(syncAppService).reprocessReservedEvent("event-key", "{}");
        verify(observabilityRecorder).recordSuccess("organization", event.getEventTopic(), event.getEventKey());
    }

    @Test
    @DisplayName("records retry metric when failed replay remains pending retry")
    void retryDueEvents_retryableFailure_recordsRetryMetric() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationEventLogEntity event = eventLog("event-key", "{}");
        OrganizationEventLogEntity refreshed = eventLog("event-key", "{}");
        refreshed.setStatus(OrganizationEventStatus.PENDING_RETRY);
        refreshed.setRetryCount(2);
        refreshed.setLastErrorCode("DIRECTORY_GATEWAY_RETRYABLE");
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.getRetry().setBatchSize(50);
        when(inboxService.findDueRetries(now, 50)).thenReturn(List.of(event));
        when(inboxService.claimDueRetry("event-key", now)).thenReturn(true);
        when(syncAppService.reprocessReservedEvent("event-key", "{}")).thenReturn(response("500"));
        when(inboxService.findByEventKey("event-key")).thenReturn(Optional.of(refreshed));

        OrganizationEventRetrySummary summary = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                properties,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true),
                observabilityRecorder
        ).retryDueEvents(now);

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.successCount()).isZero();
        assertThat(summary.failedCount()).isEqualTo(1);
        verify(observabilityRecorder).recordRetry(
                org.mockito.ArgumentMatchers.eq("organization"),
                org.mockito.ArgumentMatchers.eq(event.getEventTopic()),
                org.mockito.ArgumentMatchers.eq(event.getEventKey()),
                org.mockito.ArgumentMatchers.eq(2),
                org.mockito.ArgumentMatchers.any()
        );
        verify(observabilityRecorder, never()).recordDeadLetter("organization", event.getEventTopic(), event.getEventKey(), "DIRECTORY_GATEWAY_RETRYABLE");
    }

    @Test
    @DisplayName("records dead letter metric when failed replay remains dead letter")
    void retryDueEvents_deadLetterFailure_recordsDeadLetterMetric() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationEventLogEntity event = eventLog("event-key", "{}");
        OrganizationEventLogEntity refreshed = eventLog("event-key", "{}");
        refreshed.setStatus(OrganizationEventStatus.DEAD_LETTER);
        refreshed.setRetryCount(3);
        refreshed.setLastErrorCode("DIRECTORY_GATEWAY_NON_RETRYABLE");
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.getRetry().setBatchSize(50);
        when(inboxService.findDueRetries(now, 50)).thenReturn(List.of(event));
        when(inboxService.claimDueRetry("event-key", now)).thenReturn(true);
        when(syncAppService.reprocessReservedEvent("event-key", "{}")).thenReturn(response("500"));
        when(inboxService.findByEventKey("event-key")).thenReturn(Optional.of(refreshed));

        OrganizationEventRetrySummary summary = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                properties,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true),
                observabilityRecorder
        ).retryDueEvents(now);

        assertThat(summary.totalCount()).isEqualTo(1);
        assertThat(summary.successCount()).isZero();
        assertThat(summary.failedCount()).isEqualTo(1);
        verify(observabilityRecorder).recordDeadLetter("organization", event.getEventTopic(), event.getEventKey(), "DIRECTORY_GATEWAY_NON_RETRYABLE");
        verify(observabilityRecorder, never()).recordRetry(
                org.mockito.ArgumentMatchers.eq("organization"),
                org.mockito.ArgumentMatchers.eq(event.getEventTopic()),
                org.mockito.ArgumentMatchers.eq(event.getEventKey()),
                org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("skips retry when integration is disabled dynamically")
    void retryDueEvents_disabled_returnsEmpty() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();

        OrganizationEventRetrySummary summary = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                properties,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(false),
                observabilityRecorder
        ).retryDueEvents(now);

        assertThat(summary.totalCount()).isZero();
        verify(inboxService, never()).findDueRetries(now, properties.getRetry().getBatchSize());
    }

    @Test
    @DisplayName("manual dead letter replay claims the original log before reprocessing payload")
    void replayDeadLetter_claimsAndReprocessesOriginalPayload() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationEventLogEntity event = eventLog(" event-key ", "{\"data\":{\"userId\":\"10001\"}}");
        event.setStatus(OrganizationEventStatus.DEAD_LETTER);
        when(inboxService.findByEventKey("event-key")).thenReturn(Optional.of(event));
        when(inboxService.claimDeadLetterReplay("event-key", now)).thenReturn(true);
        when(syncAppService.reprocessReservedEvent("event-key", event.getRawPayload())).thenReturn(response("200"));

        OrganizationEventWebhookResponse response = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                new OrganizationIntegrationProperties(),
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true),
                observabilityRecorder
        ).replayDeadLetter(" event-key ", now);

        assertThat(response.code()).isEqualTo("200");
        verify(inboxService).claimDeadLetterReplay("event-key", now);
        verify(syncAppService).reprocessReservedEvent("event-key", "{\"data\":{\"userId\":\"10001\"}}");
    }

    @Test
    @DisplayName("manual replay rejects non dead letter logs")
    void replayDeadLetter_nonDeadLetter_rejects() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationEventLogEntity event = eventLog("event-key", "{}");
        when(inboxService.findByEventKey("event-key")).thenReturn(Optional.of(event));

        OrganizationEventRetryAppService service = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                new OrganizationIntegrationProperties(),
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true),
                observabilityRecorder
        );

        assertThatThrownBy(() -> service.replayDeadLetter("event-key", now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DEAD_LETTER");
        verify(inboxService, never()).claimDeadLetterReplay("event-key", now);
        verify(syncAppService, never()).reprocessReservedEvent("event-key", "{}");
    }

    @Test
    @DisplayName("manual replay rejects claim races before reprocessing")
    void replayDeadLetter_claimRace_rejects() {
        LocalDateTime now = LocalDateTime.parse("2026-05-15T10:00:00");
        OrganizationEventLogEntity event = eventLog("event-key", "{}");
        event.setStatus(OrganizationEventStatus.DEAD_LETTER);
        when(inboxService.findByEventKey("event-key")).thenReturn(Optional.of(event));
        when(inboxService.claimDeadLetterReplay("event-key", now)).thenReturn(false);

        OrganizationEventRetryAppService service = new OrganizationEventRetryAppService(
                inboxService,
                syncAppService,
                new OrganizationIntegrationProperties(),
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true),
                observabilityRecorder
        );

        assertThatThrownBy(() -> service.replayDeadLetter("event-key", now))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("状态已变化");
        verify(syncAppService, never()).reprocessReservedEvent("event-key", "{}");
    }

    private OrganizationEventLogEntity eventLog(String eventKey, String rawPayload) {
        OrganizationEventLogEntity event = new OrganizationEventLogEntity();
        event.setEventKey(eventKey);
        event.setEventTopic("BaseOssUser");
        event.setRawPayload(rawPayload);
        event.setStatus(OrganizationEventStatus.PENDING_RETRY);
        return event;
    }

    private OrganizationEventWebhookResponse response(String code) {
        return new OrganizationEventWebhookResponse(
                code,
                "success",
                1L,
                new OrganizationEventWebhookData("event-key", code.equals("200"), false, "PROCESSED")
        );
    }
}
