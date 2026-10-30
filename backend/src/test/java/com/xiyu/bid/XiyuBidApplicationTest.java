package com.xiyu.bid;

import com.xiyu.maintenance.CaseProvenancePreviewApplication;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class XiyuBidApplicationTest {
    @Test
    void routesOnlyExplicitPreviewToIsolatedMaintenanceContext() {
        assertThat(XiyuBidApplication.applicationClassFor(new String[]{
                "--case-provenance-backfill=preview"}))
                .isEqualTo(CaseProvenancePreviewApplication.class);
        assertThat(XiyuBidApplication.applicationClassFor(new String[]{
                "--case-provenance-backfill=apply"}))
                .isEqualTo(CaseProvenancePreviewApplication.class);
        assertThat(XiyuBidApplication.applicationClassFor(new String[]{"--server.port=18080"}))
                .isEqualTo(XiyuBidApplication.class);
        assertThat(XiyuBidApplication.maintenanceSafetyDefaults(new String[]{
                "--case-provenance-backfill=preview"}))
                .containsEntry("spring.flyway.enabled", "false")
                .containsEntry("spring.datasource.hikari.read-only", "true")
                .containsEntry("spring.main.web-application-type", "none")
                .containsEntry("xiyu.integrations.organization.event-sdk.enabled", "false")
                .containsEntry("xiyu.integrations.tender-event.sdk.enabled", "false")
                .containsEntry("xiyu.opportunity-history-replay.enabled", "false");
        assertThat(XiyuBidApplication.maintenanceSafetyDefaults(new String[]{
                "--case-provenance-backfill=apply"}))
                .containsEntry("spring.flyway.enabled", "false")
                .containsEntry("spring.datasource.hikari.read-only", "false")
                .containsEntry("spring.main.web-application-type", "none")
                .containsEntry("spring.task.scheduling.enabled", "false");
    }
}
