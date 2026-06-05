package com.xiyu.bid.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleProfileCatalogTenderLifecycleTest {

    @Test
    void salesShouldHaveProjectInitiationCapabilities() {
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.SALES_CODE).menuPermissions())
                .contains("project.create", "project.view", "deposit.return.fill");
    }

    @Test
    void bidLeadShouldHaveExecutionCapabilities() {
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_LEAD_CODE).menuPermissions())
                .contains("task.assign", "evaluation.update", "result.register",
                        "retrospective.submit", "closure.request");
    }

    @Test
    void bidAdminShouldHaveReviewCapabilities() {
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_ADMIN_CODE).menuPermissions())
                .contains("task.review", "retrospective.review", "closure.review", "lead.assign");
    }

    @Test
    void taskExecutorShouldHaveOwnTaskCapabilities() {
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.TASK_EXECUTOR_CODE).menuPermissions())
                .contains("task.view.own", "task.handle.own");
    }
}
