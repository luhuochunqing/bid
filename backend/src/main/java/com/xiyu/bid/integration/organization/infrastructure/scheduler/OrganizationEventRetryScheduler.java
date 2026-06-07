package com.xiyu.bid.integration.organization.infrastructure.scheduler;

import com.xiyu.bid.integration.organization.application.OrganizationEventRetryAppService;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OrganizationEventRetryScheduler {
    private final OrganizationEventRetryAppService retryAppService;
    private final OrganizationIntegrationProperties properties;

    @Scheduled(fixedDelayString = "${xiyu.integrations.organization.retry.fixed-delay-ms:60000}")
    public void retryDueEvents() {
        if (properties.isEnabled() && properties.getRetry().isEnabled()) {
            retryAppService.retryDueEvents(LocalDateTime.now());
        }
    }
}
