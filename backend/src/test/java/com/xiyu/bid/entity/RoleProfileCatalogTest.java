package com.xiyu.bid.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleProfileCatalogTest {

    @Test
    @DisplayName("业务人员默认拥有工作台快速发起和 AI 中心权限")
    void staffRoleShouldIncludeQuickStartAndAiCenterPermission() {
        RoleProfileCatalog.SeedDefinition definition =
                RoleProfileCatalog.definitionForCode(RoleProfileCatalog.STAFF_CODE);

        assertThat(definition.menuPermissions())
                .contains("dashboard", "operation-logs", RoleProfileCatalog.QUICK_START_PERMISSION,
                        RoleProfileCatalog.AI_CENTER_PERMISSION);
    }

    @Test
    @DisplayName("审计员默认拥有审计日志和个人操作日志入口权限")
    void auditorRoleShouldIncludeAuditAndOperationLogPermissions() {
        RoleProfileCatalog.SeedDefinition definition =
                RoleProfileCatalog.definitionForCode(RoleProfileCatalog.AUDITOR_CODE);

        assertThat(definition.menuPermissions()).contains("audit-logs", "operation-logs");
        assertThat(RoleProfileCatalog.legacyRoleForCode(RoleProfileCatalog.AUDITOR_CODE)).isEqualTo(User.Role.STAFF);
    }
}
