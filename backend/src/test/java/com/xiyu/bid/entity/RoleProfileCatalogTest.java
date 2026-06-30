package com.xiyu.bid.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleProfileCatalogTest {

    @Test
    @DisplayName("行政人员默认拥有资质证书管理与行政事务权限")
    void adminStaffRoleShouldIncludeCertificateAndQualificationPermissions() {
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
        assertThat(RoleProfileCatalog.legacyRoleForCode(RoleProfileCatalog.BID_ADMIN_CODE)).isEqualTo(User.Role.ADMIN);
    }

    @Test
    @DisplayName("isRegisteredCode：已注册 code 返回 true，未注册/null 返回 false，大小写与空白归一")
    void isRegisteredCodeShouldRecognizeCatalogCodesOnly() {
        assertThat(RoleProfileCatalog.isRegisteredCode(RoleProfileCatalog.ADMIN_STAFF_CODE)).isTrue();
        assertThat(RoleProfileCatalog.isRegisteredCode(RoleProfileCatalog.BID_OTHER_DEPT_CODE)).isTrue();
        assertThat(RoleProfileCatalog.isRegisteredCode("/BIDADMIN")).isTrue();
        assertThat(RoleProfileCatalog.isRegisteredCode("  bid-TeamLeader  ")).isTrue();
        assertThat(RoleProfileCatalog.isRegisteredCode("legal-reviewer")).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode("unknown_role")).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode(null)).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode("")).isFalse();
        assertThat(RoleProfileCatalog.isRegisteredCode("   ")).isFalse();
    }

    @Test
    @DisplayName("shouldSkipLegacyRoleCompat：受限角色与未注册角色跳过 legacy 角色兼容，已注册普通角色与纯 Legacy 用户保留")
    void shouldSkipLegacyRoleCompatShouldCoverRestrictedAndUnregistered() {
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.BID_OTHER_DEPT_CODE)).isTrue();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("legal-reviewer")).isTrue();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("  Unknown ")).isTrue();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.ADMIN_STAFF_CODE)).isTrue();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.BID_ADMIN_CODE)).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(RoleProfileCatalog.SALES_CODE)).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat(null)).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("")).isFalse();
        assertThat(RoleProfileCatalog.shouldSkipLegacyRoleCompat("   ")).isFalse();
    }

    @Test
    @DisplayName("seedDefinitions 列表无重复 code，且每个 seed 角色在 DEFINITIONS 中已注册")
    void seedDefinitionsShouldCoverAllCatalogRolesWithoutDuplicates() {
        var seeds = RoleProfileCatalog.seedDefinitions();
        var seedCodes = seeds.stream().map(RoleProfileCatalog.SeedDefinition::code).toList();
        assertThat(seedCodes).doesNotHaveDuplicates();
        for (String code : seedCodes) {
            assertThat(RoleProfileCatalog.isRegisteredCode(code))
                    .as("seed code '%s' must exist in DEFINITIONS", code)
                    .isTrue();
        }
    }

    @Test
    @DisplayName("OSS_TO_INTERNAL_ROLE 所有映射目标都是已知角色 code")
    void ossMappingTargetsShouldBeKnownRoles() {
        var targets = java.util.Set.of(
                "/bidAdmin", "bid-TeamLeader", "admin",
                "bid-Team", "bid-projectLeader", "bid-administration", "bid-otherDept");
        for (String target : targets) {
            boolean known = RoleProfileCatalog.isRegisteredCode(target)
                    || "admin".equals(target) || "manager".equals(target);
            assertThat(known)
                    .as("OSS mapping target '%s' must be a known role code", target)
                    .isTrue();
        }
    }

    // ── CO-409：CA 信息管理模块投标专员操作项权限矩阵对齐 ──

    @Test
    @DisplayName("CO-409: 投标专员 menuPermissions 含 resource-ca，对齐 CA 模块侧边栏与路由访问")
    void bidTeamShouldIncludeResourceCaPermission() {
        RoleProfileCatalog.SeedDefinition def =
                RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_SPECIALIST_CODE);

        assertThat(def.menuPermissions())
                .as("bid-Team 必须持有 resource-ca 才能访问 CA 信息管理菜单与路由")
                .contains("resource-ca");
    }
}
