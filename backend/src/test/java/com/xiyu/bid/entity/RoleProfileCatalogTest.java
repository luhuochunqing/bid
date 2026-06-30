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

    // ── CO-394-A：品牌授权权限点对齐（三角色一致性） ──

    @Test
    @DisplayName("CO-394: 投标组长/管理员/专员三角色 menuPermissions 含 brand-auth.view/create/edit")
    void knowledgeRolesShouldIncludeBrandAuthViewCreateEditPermissions() {
        String[] targetRoles = {
                RoleProfileCatalog.BID_LEAD_CODE,
                RoleProfileCatalog.BID_ADMIN_CODE,
                RoleProfileCatalog.BID_SPECIALIST_CODE
        };
        for (String code : targetRoles) {
            RoleProfileCatalog.SeedDefinition def = RoleProfileCatalog.definitionForCode(code);
            assertThat(def.menuPermissions())
                    .as("%s 必须持有 brand-auth.view/create/edit 才能访问品牌授权模块",
                            RoleProfileCatalog.canonicalCode(code))
                    .contains(
                            RoleProfileCatalog.BRAND_AUTH_VIEW_PERMISSION,
                            RoleProfileCatalog.BRAND_AUTH_CREATE_PERMISSION,
                            RoleProfileCatalog.BRAND_AUTH_EDIT_PERMISSION);
        }
    }

    @Test
    @DisplayName("CO-394: 投标组长/管理员两角色含 brand-auth.revoke，投标专员不含（撤销权限收窄）")
    void revokePermissionShouldBeGrantedToLeadAndAdminOnly() {
        // 投标组长、投标管理员可撤销
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_LEAD_CODE)
                .menuPermissions())
                .contains(RoleProfileCatalog.BRAND_AUTH_REVOKE_PERMISSION);
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_ADMIN_CODE)
                .menuPermissions())
                .contains(RoleProfileCatalog.BRAND_AUTH_REVOKE_PERMISSION);
        // 投标专员不可撤销（仅 view/create/edit）
        assertThat(RoleProfileCatalog.definitionForCode(RoleProfileCatalog.BID_SPECIALIST_CODE)
                .menuPermissions())
                .doesNotContain(RoleProfileCatalog.BRAND_AUTH_REVOKE_PERMISSION);
    }

    // ── CO-394-B：人员证书管理权限点对齐（三角色一致性） ──

    @Test
    @DisplayName("CO-394: 投标组长/管理员/专员三角色 menuPermissions 含 personnel.manage（写操作权限）")
    void knowledgeRolesShouldIncludePersonnelManagePermission() {
        String[] targetRoles = {
                RoleProfileCatalog.BID_LEAD_CODE,
                RoleProfileCatalog.BID_ADMIN_CODE,
                RoleProfileCatalog.BID_SPECIALIST_CODE
        };
        for (String code : targetRoles) {
            RoleProfileCatalog.SeedDefinition def = RoleProfileCatalog.definitionForCode(code);
            assertThat(def.menuPermissions())
                    .as("%s 必须持有 personnel.manage 才能执行人员库写操作（新增/编辑/删除/导入）",
                            RoleProfileCatalog.canonicalCode(code))
                    .contains(RoleProfileCatalog.PERSONNEL_MANAGE_PERMISSION);
        }
    }

    @Test
    @DisplayName("CO-394: 三角色仍保留 personnel.view（只读权限），view 与 manage 双权限点共存")
    void knowledgeRolesShouldRetainPersonnelViewPermission() {
        String[] targetRoles = {
                RoleProfileCatalog.BID_LEAD_CODE,
                RoleProfileCatalog.BID_ADMIN_CODE,
                RoleProfileCatalog.BID_SPECIALIST_CODE
        };
        for (String code : targetRoles) {
            assertThat(RoleProfileCatalog.definitionForCode(code).menuPermissions())
                    .as("%s 应保留 personnel.view 只读权限", code)
                    .contains(RoleProfileCatalog.PERSONNEL_VIEW_PERMISSION);
        }
    }

    // ── CO-394-C：业绩管理权限点对齐（三角色一致性） ──

    @Test
    @DisplayName("CO-394: 投标组长/管理员/专员三角色 menuPermissions 含 performance.manage")
    void knowledgeRolesShouldIncludePerformanceManagePermission() {
        String[] targetRoles = {
                RoleProfileCatalog.BID_LEAD_CODE,
                RoleProfileCatalog.BID_ADMIN_CODE,
                RoleProfileCatalog.BID_SPECIALIST_CODE
        };
        for (String code : targetRoles) {
            assertThat(RoleProfileCatalog.definitionForCode(code).menuPermissions())
                    .as("%s 必须持有 performance.manage 才能访问业绩管理模块",
                            RoleProfileCatalog.canonicalCode(code))
                    .contains(RoleProfileCatalog.PERFORMANCE_MANAGE_PERMISSION);
        }
    }

    // ── CO-394-D：资质证书管理权限点对齐（三角色一致性） ──

    @Test
    @DisplayName("CO-394: 投标组长/管理员/专员三角色 menuPermissions 含 qualification.manage")
    void knowledgeRolesShouldIncludeQualificationManagePermission() {
        String[] targetRoles = {
                RoleProfileCatalog.BID_LEAD_CODE,
                RoleProfileCatalog.BID_ADMIN_CODE,
                RoleProfileCatalog.BID_SPECIALIST_CODE
        };
        for (String code : targetRoles) {
            assertThat(RoleProfileCatalog.definitionForCode(code).menuPermissions())
                    .as("%s 必须持有 qualification.manage 才能管理资质证书",
                            RoleProfileCatalog.canonicalCode(code))
                    .contains(RoleProfileCatalog.QUALIFICATION_MANAGE_PERMISSION);
        }
    }

    @Test
    @DisplayName("CO-394: 行政人员(bid-administration) 仅含 qualification.view 只读，不含 qualification.manage")
    void adminStaffShouldHaveQualificationViewOnlyWithoutManage() {
        RoleProfileCatalog.SeedDefinition def =
                RoleProfileCatalog.definitionForCode(RoleProfileCatalog.ADMIN_STAFF_CODE);
        assertThat(def.menuPermissions())
                .as("行政人员仅有资质只读权限")
                .contains("qualification.view")
                .doesNotContain(RoleProfileCatalog.QUALIFICATION_MANAGE_PERMISSION);
    }
}
