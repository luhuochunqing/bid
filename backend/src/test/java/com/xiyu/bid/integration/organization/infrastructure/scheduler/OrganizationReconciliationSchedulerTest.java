package com.xiyu.bid.integration.organization.infrastructure.scheduler;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationSettingsResolver;
import com.xiyu.bid.integration.organization.application.OrganizationSyncRunAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationReconciliationScheduler")
class OrganizationReconciliationSchedulerTest {
    @Mock
    private OrganizationSyncRunAppService syncRunAppService;

    @Test
    @DisplayName("skips reconciliation when reconciliation switch is disabled")
    void reconcileRecentWindow_reconciliationDisabled_skips() {
        OrganizationIntegrationProperties properties = properties(true);
        properties.getReconciliation().setEnabled(false);

        scheduler(properties).reconcileRecentWindow();

        verifyNoReconciliation();
    }

    @Test
    @DisplayName("skips reconciliation when organization integration is disabled")
    void reconcileRecentWindow_integrationDisabled_skips() {
        OrganizationIntegrationProperties properties = properties(false);
        properties.getReconciliation().setEnabled(true);

        scheduler(properties).reconcileRecentWindow();

        verifyNoReconciliation();
    }

    private OrganizationReconciliationScheduler scheduler(OrganizationIntegrationProperties properties) {
        return new OrganizationReconciliationScheduler(
                syncRunAppService,
                properties,
                new OrganizationIntegrationSettingsResolver(null, properties)
        );
    }

    private OrganizationIntegrationProperties properties(boolean enabled) {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(enabled);
        return properties;
    }

    private void verifyNoReconciliation() {
        verify(syncRunAppService, never()).syncWindow(
                eq("oss"),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                eq("RECONCILIATION")
        );
    }
}
