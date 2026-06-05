package com.xiyu.bid.integration.organization.infrastructure.scheduler;

import com.xiyu.bid.integration.organization.application.OrganizationIntegrationProperties;
import com.xiyu.bid.integration.organization.application.OrganizationIntegrationSettingsResolver;
import com.xiyu.bid.integration.organization.application.OrganizationSyncRunAppService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class OrganizationReconciliationScheduler {
    private static final String SOURCE_APP = "oss";
    private static final String RUN_TYPE = "RECONCILIATION";

    private final OrganizationSyncRunAppService syncRunAppService;
    private final OrganizationIntegrationProperties properties;
    private final OrganizationIntegrationSettingsResolver settingsResolver;

    @Scheduled(cron = "${xiyu.integrations.organization.reconciliation.cron:0 30 2 * * *}")
    public void reconcileRecentWindow() {
        if (!properties.getReconciliation().isEnabled()) {
            return;
        }
        if (!settingsResolver.resolve().enabled()) {
            return;
        }
        LocalDateTime endAt = LocalDateTime.now();
        LocalDateTime startAt = endAt.minusDays(properties.getReconciliation().getLookbackDays());
        syncRunAppService.syncWindow(SOURCE_APP, startAt, endAt, RUN_TYPE);
    }
}
