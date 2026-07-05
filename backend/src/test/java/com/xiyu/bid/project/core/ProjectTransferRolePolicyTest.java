package com.xiyu.bid.project.core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * ProjectTransferRolePolicy 独立单元测试（纯核心，无 Spring/Mockito）。
 * 覆盖 GLOBAL_ACCESS_ROLES + SALES_CODE 全部角色 + 边界条件。
 */
class ProjectTransferRolePolicyTest {

    // ── 合法角色 ────────────────────────────────────────────────────────────

    @Test
    void isValidNewOwnerRole_bidProjectLeader_accepted() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("bid-projectLeader")).isTrue();
    }

    @Test
    void isValidNewOwnerRole_admin_accepted() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("admin")).isTrue();
    }

    @Test
    void isValidNewOwnerRole_bidAdmin_accepted() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("/bidAdmin")).isTrue();
    }

    @Test
    void isValidNewOwnerRole_bidTeamLeader_accepted() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("bid-TeamLeader")).isTrue();
    }

    // ── 非法角色 ────────────────────────────────────────────────────────────

    @Test
    void isValidNewOwnerRole_bidTeam_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("bid-Team")).isFalse();
    }

    @Test
    void isValidNewOwnerRole_bidAdministration_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("bid-administration")).isFalse();
    }

    @Test
    void isValidNewOwnerRole_bidOtherDept_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("bid-otherDept")).isFalse();
    }

    // ── 边界条件 ────────────────────────────────────────────────────────────

    @Test
    void isValidNewOwnerRole_null_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole(null)).isFalse();
    }

    @Test
    void isValidNewOwnerRole_empty_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("")).isFalse();
    }

    @Test
    void isValidNewOwnerRole_blank_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("   ")).isFalse();
    }

    // ── 大小写敏感（项目硬约束：禁止对 roleCode 做大小写归一化） ─────────────

    @Test
    void isValidNewOwnerRole_caseSensitive_rejected() {
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("ADMIN")).isFalse();
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("Bid-Projectleader")).isFalse();
        assertThat(ProjectTransferRolePolicy.isValidNewOwnerRole("/BIDADMIN")).isFalse();
    }
}
