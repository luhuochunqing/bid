package com.xiyu.bid.integration.organization.application;

import com.xiyu.bid.integration.organization.domain.OrganizationEventStatus;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationEventLogRepository;
import com.xiyu.bid.integration.organization.infrastructure.persistence.repository.OrganizationSyncRunRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationOperationsAppService")
class OrganizationOperationsAppServiceTest {
    @Mock
    private OrganizationEventLogRepository eventLogRepository;
    @Mock
    private OrganizationSyncRunRepository runRepository;

    @Test
    @DisplayName("summarizes switches event backlog and latest sync run")
    void status_summarizesOperationsState() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(true);
        properties.getEventSdk().setEnabled(false);
        OrganizationSyncRunEntity run = new OrganizationSyncRunEntity();
        run.setRunKey("run-1");
        run.setRunType("RECONCILIATION");
        run.setStatus("SUCCEEDED");
        when(eventLogRepository.countByStatus(OrganizationEventStatus.PENDING_RETRY)).thenReturn(2L);
        when(eventLogRepository.countByStatus(OrganizationEventStatus.DEAD_LETTER)).thenReturn(1L);
        when(eventLogRepository.countByStatus(OrganizationEventStatus.FAILED)).thenReturn(0L);
        when(runRepository.findTopByOrderByStartedAtDesc()).thenReturn(Optional.of(run));

        OrganizationOperationsStatusResponse response = new OrganizationOperationsAppService(
                eventLogRepository,
                runRepository,
                properties,
                OrganizationDirectorySyncAppServiceTest.fixedSettings(true)
        ).status();

        assertThat(response.enabled()).isTrue();
        assertThat(response.eventSdkEnabled()).isFalse();
        assertThat(response.pendingRetryCount()).isEqualTo(2L);
        assertThat(response.deadLetterCount()).isEqualTo(1L);
        assertThat(response.failedCount()).isZero();
        assertThat(response.lastRun().runKey()).isEqualTo("run-1");
    }
}
