package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationEventLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrganizationEventLogRetentionService — retention cleanup")
class OrganizationEventLogRetentionServiceTest {

    @Test
    @DisplayName("deletes event logs older than configured retention days")
    void cleanupExpiredLogs_deletesOlderLogs() {
        OrganizationEventLogRepository repository = mock(OrganizationEventLogRepository.class);
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEventLogRetentionDays(30);
        when(repository.deleteByReceivedAtBefore(any(LocalDateTime.class))).thenReturn(12);
        OrganizationEventLogRetentionService service = new OrganizationEventLogRetentionService(repository, properties);

        int deleted = service.cleanupExpiredLogs();

        assertThat(deleted).isEqualTo(12);
        verify(repository).deleteByReceivedAtBefore(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("retention cleanup can be disabled with non-positive days")
    void cleanupExpiredLogs_nonPositiveRetentionSkipsDelete() {
        OrganizationEventLogRepository repository = mock(OrganizationEventLogRepository.class);
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEventLogRetentionDays(0);
        OrganizationEventLogRetentionService service = new OrganizationEventLogRetentionService(repository, properties);

        int deleted = service.cleanupExpiredLogs();

        assertThat(deleted).isZero();
        verify(repository, never()).deleteByReceivedAtBefore(any());
    }
}
