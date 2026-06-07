package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.dto.OrganizationEventWebhookResponse;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationEventLogEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrganizationEventRetryAppService {
    private static final long PROCESSING_LEASE_MINUTES = 15;

    private final OrganizationEventInboxService inboxService;
    private final OrganizationDirectorySyncAppService syncAppService;
    private final OrganizationIntegrationProperties properties;
    private final OrganizationIntegrationSettingsResolver settingsResolver;

    public OrganizationEventRetrySummary retryDueEvents(LocalDateTime now) {
        if (!settingsResolver.resolve().enabled() || !properties.getRetry().isEnabled()) {
            return OrganizationEventRetrySummary.empty();
        }
        inboxService.recoverStaleProcessing(now.minusMinutes(PROCESSING_LEASE_MINUTES), now);
        OrganizationEventRetrySummary summary = OrganizationEventRetrySummary.empty();
        for (OrganizationEventLogEntity event : inboxService.findDueRetries(now, properties.getRetry().getBatchSize())) {
            summary = summary.add(retryOne(event, now));
        }
        return summary;
    }

    public OrganizationEventWebhookResponse replayDeadLetter(String eventKey, LocalDateTime now) {
        String normalizedEventKey = normalizeEventKey(eventKey);
        if (!settingsResolver.resolve().enabled()) {
            throw new IllegalStateException("组织架构事件接入已关闭");
        }
        OrganizationEventLogEntity event = inboxService.findByEventKey(normalizedEventKey)
                .orElseThrow(() -> new ResourceNotFoundException("OrganizationEventLog", normalizedEventKey));
        if (event.getStatus() != OrganizationEventStatus.DEAD_LETTER) {
            throw new IllegalStateException("只有 DEAD_LETTER 状态的组织架构事件可以重放");
        }
        if (!inboxService.claimDeadLetterReplay(normalizedEventKey, now)) {
            throw new IllegalStateException("死信事件状态已变化，请刷新后重试");
        }
        return syncAppService.reprocessReservedEvent(normalizedEventKey, event.getRawPayload());
    }

    private OrganizationEventRetrySummary retryOne(OrganizationEventLogEntity event, LocalDateTime now) {
        if (!inboxService.claimDueRetry(event.getEventKey(), now)) {
            return OrganizationEventRetrySummary.empty();
        }
        OrganizationEventWebhookResponse response =
                syncAppService.reprocessReservedEvent(event.getEventKey(), event.getRawPayload());
        boolean success = "200".equals(response.code());
        return new OrganizationEventRetrySummary(1, success ? 1 : 0, success ? 0 : 1);
    }

    private String normalizeEventKey(String eventKey) {
        if (eventKey == null || eventKey.isBlank()) {
            throw new IllegalArgumentException("事件ID必填");
        }
        return eventKey.trim();
    }
}
