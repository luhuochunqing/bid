package com.xiyu.bid.integration.organization.infrastructure.scheduler;

import com.xiyu.bid.integration.organization.application.OrganizationEventRetryAppService;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrganizationEventRetryScheduler")
class OrganizationEventRetrySchedulerTest {
    @Mock
    private OrganizationEventRetryAppService retryAppService;

    @Test
    @DisplayName("skips retry scan when organization integration is disabled")
    void retryDueEvents_integrationDisabled_skips() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(false);

        new OrganizationEventRetryScheduler(retryAppService, properties).retryDueEvents();

        verify(retryAppService, never()).retryDueEvents(any(LocalDateTime.class));
    }

    @Test
    @DisplayName("skips retry scan when retry switch is disabled")
    void retryDueEvents_retryDisabled_skips() {
        OrganizationIntegrationProperties properties = new OrganizationIntegrationProperties();
        properties.setEnabled(true);
        properties.getRetry().setEnabled(false);

        new OrganizationEventRetryScheduler(retryAppService, properties).retryDueEvents();

        verify(retryAppService, never()).retryDueEvents(any(LocalDateTime.class));
    }
}
