package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.application.OrganizationSyncRunAppService;
import com.xiyu.bid.integration.organization.dto.OrganizationSyncRunRequest;
import com.xiyu.bid.integration.organization.dto.OrganizationSyncRunResponse;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationSyncRunController - initialization and reconciliation API")
class OrganizationSyncRunControllerTest {

    @Test
    @DisplayName("starts explicit initialization window")
    void startSyncRun_explicitInitializationWindow() {
        FakeSyncRunAppService service = new FakeSyncRunAppService();
        OrganizationSyncRunController controller = new OrganizationSyncRunController(service);
        LocalDateTime startAt = LocalDateTime.of(2026, 4, 30, 0, 0);
        LocalDateTime endAt = LocalDateTime.of(2026, 5, 1, 0, 0);

        OrganizationSyncRunResponse response = controller.startSyncRun(new OrganizationSyncRunRequest(
                "oss", "initialization", startAt, endAt
        ));

        assertThat(service.sourceApp).isEqualTo("oss");
        assertThat(service.runType).isEqualTo("INITIALIZATION");
        assertThat(service.startAt).isEqualTo(startAt);
        assertThat(service.endAt).isEqualTo(endAt);
        assertThat(response.runId()).isEqualTo(88L);
        assertThat(response.status()).isEqualTo("SUCCEEDED");
    }

    private static class FakeSyncRunAppService extends OrganizationSyncRunAppService {
        String sourceApp;
        String runType;
        LocalDateTime startAt;
        LocalDateTime endAt;

        FakeSyncRunAppService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public OrganizationSyncRunEntity syncWindow(
                String sourceApp,
                LocalDateTime startAt,
                LocalDateTime endAt,
                String runType
        ) {
            this.sourceApp = sourceApp;
            this.startAt = startAt;
            this.endAt = endAt;
            this.runType = runType;
            OrganizationSyncRunEntity run = new OrganizationSyncRunEntity();
            run.setId(88L);
            run.setRunKey("run-key");
            run.setRunType(runType);
            run.setSourceApp(sourceApp);
            run.setStatus("SUCCEEDED");
            run.setTotalCount(2);
            run.setSuccessCount(2);
            return run;
        }
    }
}
