package com.xiyu.bid.integration.organization.controller;

import com.xiyu.bid.integration.organization.application.OrganizationManualResyncAppService;
import com.xiyu.bid.integration.organization.dto.OrganizationSyncRunResponse;
import com.xiyu.bid.integration.organization.infrastructure.persistence.entity.OrganizationSyncRunEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Principal;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationManualResyncController")
class OrganizationManualResyncControllerTest {

    @Test
    @DisplayName("resync user delegates to app service with authenticated operator")
    void resyncUser_delegatesWithOperator() {
        FakeManualResyncAppService service = new FakeManualResyncAppService();
        OrganizationManualResyncController controller = new OrganizationManualResyncController(service);

        OrganizationSyncRunResponse response = controller.resyncUser("10001", () -> "admin");

        assertThat(service.userId).isEqualTo("10001");
        assertThat(service.triggeredBy).isEqualTo("admin");
        assertThat(response.runType()).isEqualTo("MANUAL_USER_RESYNC");
    }

    @Test
    @DisplayName("resync department delegates to app service")
    void resyncDepartment_delegates() {
        FakeManualResyncAppService service = new FakeManualResyncAppService();
        OrganizationManualResyncController controller = new OrganizationManualResyncController(service);

        OrganizationSyncRunResponse response = controller.resyncDepartment("D001", anonymous());

        assertThat(service.deptId).isEqualTo("D001");
        assertThat(service.triggeredBy).isEqualTo("system");
        assertThat(response.runType()).isEqualTo("MANUAL_DEPARTMENT_RESYNC");
    }

    private Principal anonymous() {
        return null;
    }

    private static class FakeManualResyncAppService extends OrganizationManualResyncAppService {
        String userId;
        String deptId;
        String triggeredBy;

        FakeManualResyncAppService() {
            super(null, null, null, null, null, null);
        }

        @Override
        public OrganizationSyncRunEntity resyncUser(String sourceApp, String userId, String triggeredBy) {
            this.userId = userId;
            this.triggeredBy = triggeredBy;
            return run("MANUAL_USER_RESYNC");
        }

        @Override
        public OrganizationSyncRunEntity resyncDepartment(String sourceApp, String deptId, String triggeredBy) {
            this.deptId = deptId;
            this.triggeredBy = triggeredBy;
            return run("MANUAL_DEPARTMENT_RESYNC");
        }

        private OrganizationSyncRunEntity run(String runType) {
            OrganizationSyncRunEntity run = new OrganizationSyncRunEntity();
            run.setId(1L);
            run.setRunKey("run-key");
            run.setRunType(runType);
            run.setSourceApp("oss");
            run.setStatus("SUCCEEDED");
            run.setTotalCount(1);
            run.setSuccessCount(1);
            return run;
        }
    }
}
