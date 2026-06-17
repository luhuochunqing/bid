package com.xiyu.bid.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleProfileCatalogTest {

    @Test
    @DisplayName("业务人员默认拥有工作台快速发起和 AI 中心权限")
    void staffRoleShouldIncludeQuickStartAndAiCenterPermission() {
        RoleProfileCatalog.SeedDefinition definition =
                RoleProfileCatalog.definitionForCode(RoleProfileCatalog.ADMIN_STAFF_CODE);

        assertThat(definition.menuPermissions())
                .contains("certificate.manage", "qualification.view");
    }

    @Test
    @DisplayName("投标管理员默认拥有操作日志和设置入口权限")
    void bidAdminRoleShouldIncludeOperationLogAndSettingsPermissions() {
        RoleProfileCatalog.SeedDefinition definition =
                RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_ADMIN_CODE);

        assertThat(definition.menuPermissions()).contains("operation-logs", "settings");
        assertThat(RoleProfileCatalog.legacyRoleForCode(RoleProfileCatalog.BID_ADMIN_CODE)).isEqualTo(User.Role.MANAGER);
    }

    @Test
    @DisplayName("isRegisteredCode：已注册 code 返回 true，未注册/null 返回 false，大小写与空白归一")
    void isRegisteredCodeShouldRecognizeCatalogCodesOnly() {
        assertThat(RoleProfileCatalog.isRegisteredCode(RoleProfileCatalog.ADMIN_STAFF_CODE)).isTrue();
        assertThat(RoleProfileCatalog.isRegisteredCode(RoleProfileCatalog.BID_OTHER_DEPT_CODE)).isTrue();
        assertThat(RoleProfileCatalog.isRegisteredCode("BID_ADMIN")).isTrue();      // 大小写归一
        assertThat(RoleProfileCatalog.isRegisteredCode("  bid_lead  ")).isTrue();   // trim
        assertThat(RoleProfileCatalog.isRegisteredCode("legal-reviewer")).isFalse(); // 未注册
        assertThat(RoleProfileCatalog.isRegisteredCode("unknown_role")).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode(null)).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode("")).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode("   ")).isFalse();
    }

    @Test
    @DisplayName("shouldSkipLegacyRoleCompat：受限角色与未注册角色跳过 STAFF 兼容，已注册普通角色与纯 Legacy 用户保留")
    void shouldSkipLegacyRoleCompatShouldCoverRestrictedAndUnregistered() {
        // 显式标记的新式受限角色：跳过
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.BID_OTHER_DEPT_CODE)).isTrue();
        // 未注册角色：跳过（防御手动 INSERT 的角色误拿 STAFF fallback）
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("legal-reviewer")).isTrue();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("  Unknown ")).isTrue();
        // 已注册的普通角色：不跳过（保留 STAFF/ADMIN/MANAGER 兼容）
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.ADMIN_STAFF_CODE)).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.BID_ADMIN_CODE)).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.SALES_CODE)).isFalse();
        // 纯 Legacy 用户（roleCode 为空）：不跳过，保留 user.getRole() 鉴权
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(null)).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("")).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("   ")).isFalse();
    }
}
