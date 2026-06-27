package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.RoleProfileCatalog;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CO-361: 任务可见性策略单元测试。
 *
 * <p>验证基于角色 + 项目身份的任务可见性判断：
 * <ul>
 *   <li>投标管理角色（admin/ bidAdmin/ bid-TeamLeader）→ 看项目所有任务</li>
 *   <li>投标专员（bid-Team）且是项目投标负责人/辅助 → 看该项目所有任务</li>
 *   <li>其他角色（项目负责人 bid-projectLeader、跨部门 bid-otherDept 等）→ 只看自己的任务</li>
 * </ul>
 */
class TaskVisibilityPolicyTest {

    @Test
    void shouldViewAllTasks_whenUserIsBidAdmin() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.BID_ADMIN_CODE,
                100L,
                200L,
                300L
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldViewAllTasks_whenUserIsBidTeamLeader() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.BID_LEAD_CODE,
                100L,
                200L,
                300L
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldViewAllTasks_whenUserIsAdmin() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.ADMIN_CODE,
                100L,
                200L,
                300L
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldViewAllTasks_whenBidSpecialistIsPrimaryLeadOfProject() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.BID_SPECIALIST_CODE,
                100L,
                100L,
                null
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldViewAllTasks_whenBidSpecialistIsSecondaryLeadOfProject() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.BID_SPECIALIST_CODE,
                100L,
                null,
                100L
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldViewOnlyOwnTasks_whenBidSpecialistIsNotProjectLead() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.BID_SPECIALIST_CODE,
                999L,
                100L,
                200L
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldViewOnlyOwnTasks_whenUserIsProjectLeader() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.SALES_CODE,
                100L,
                100L,
                200L
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldViewOnlyOwnTasks_whenUserIsBidOtherDept() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.BID_OTHER_DEPT_CODE,
                100L,
                100L,
                200L
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldViewOnlyOwnTasks_whenUserIsAdminStaff() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                RoleProfileCatalog.ADMIN_STAFF_CODE,
                100L,
                100L,
                200L
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldViewOnlyOwnTasks_whenRoleCodeIsNull() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                null,
                100L,
                100L,
                200L
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldViewOnlyOwnTasks_whenRoleCodeIsBlank() {
        boolean result = TaskVisibilityPolicy.canViewAllProjectTasks(
                "  ",
                100L,
                100L,
                200L
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldQueryByProject_whenUserIsBidAdmin() {
        boolean result = TaskVisibilityPolicy.shouldQueryByProjectScope(
                RoleProfileCatalog.BID_ADMIN_CODE
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldQueryByProject_whenUserIsBidTeamLeader() {
        boolean result = TaskVisibilityPolicy.shouldQueryByProjectScope(
                RoleProfileCatalog.BID_LEAD_CODE
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldQueryByProject_whenUserIsBidSpecialist() {
        boolean result = TaskVisibilityPolicy.shouldQueryByProjectScope(
                RoleProfileCatalog.BID_SPECIALIST_CODE
        );
        assertThat(result).isTrue();
    }

    @Test
    void shouldQueryByAssigneeOnly_whenUserIsProjectLeader() {
        boolean result = TaskVisibilityPolicy.shouldQueryByProjectScope(
                RoleProfileCatalog.SALES_CODE
        );
        assertThat(result).isFalse();
    }

    @Test
    void shouldQueryByAssigneeOnly_whenUserIsBidOtherDept() {
        boolean result = TaskVisibilityPolicy.shouldQueryByProjectScope(
                RoleProfileCatalog.BID_OTHER_DEPT_CODE
        );
        assertThat(result).isFalse();
    }
}
