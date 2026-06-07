package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationEventLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class OrganizationEventLogRetentionService {

    private final OrganizationEventLogRepository eventLogRepository;
    private final OrganizationIntegrationProperties properties;

    @Transactional
    @Scheduled(cron = "${xiyu.integrations.organization.retention-cleanup-cron:0 25 2 * * ?}")
    public int cleanupExpiredLogs() {
        int retentionDays = properties.getEventLogRetentionDays();
        if (retentionDays <= 0) {
            return 0;
        }
        return eventLogRepository.deleteByReceivedAtBefore(LocalDateTime.now().minusDays(retentionDays));
    }
}
