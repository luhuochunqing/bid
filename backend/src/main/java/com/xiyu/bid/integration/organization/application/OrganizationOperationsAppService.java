package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationEventLogRepository;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationSyncRunRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrganizationOperationsAppService {
    private final OrganizationEventLogRepository eventLogRepository;
    private final OrganizationSyncRunRepository runRepository;
    private final OrganizationIntegrationProperties properties;
    private final OrganizationIntegrationSettingsResolver settingsResolver;

    public OrganizationOperationsStatusResponse status() {
        return new OrganizationOperationsStatusResponse(
                settingsResolver.resolve().enabled(),
                properties.getEventSdk().isEnabled(),
                eventLogRepository.countByStatus(OrganizationEventStatus.PENDING_RETRY),
                eventLogRepository.countByStatus(OrganizationEventStatus.DEAD_LETTER),
                eventLogRepository.countByStatus(OrganizationEventStatus.FAILED),
                runRepository.findTopByOrderByStartedAtDesc()
                        .map(OrganizationOperationsStatusResponse.LastRun::from)
                        .orElse(null)
        );
    }
}
