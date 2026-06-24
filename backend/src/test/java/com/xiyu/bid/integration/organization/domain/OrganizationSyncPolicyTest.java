package com.xiyu.bid.integration.organization.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrganizationSyncPolicy - pure sync decisions")
class OrganizationSyncPolicyTest {

    @Test
    @DisplayName("builds idempotency key from source topic key and time")
    void idempotencyKey_usesCustomerNoticeIdentity() {
        OrganizationEventNotice notice = new OrganizationEventNotice(
                "trace-1",
                "span-1",
                "parent-1",
                "customer-org",
                OrganizationEventType.USER_NOTICE,
                "2026-04-30T10:15:30+08:00",
                "user-10001",
                "10001"
        );

        assertThat(OrganizationSyncPolicy.idempotencyKey(notice))
                .isEqualTo("customer-org|BaseOssUser|user-10001|2026-04-30T10:15:30+08:00");
    }

    @Test
    @DisplayName("unknown external role returns null")
    void planUserSync_unknownRole_returnsNull() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "boss", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                null,
                Set.of("boss"),
                Set.of()
        );

        assertThat(plan.roleCode()).isNull();
    }

    @Test
    @DisplayName("existing manager is not automatically promoted to admin")
    void planUserSync_adminRole_doesNotAutoElevate() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "external-admin", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                "manager",
                Set.of("external-admin"),
                Set.of()
        );

        assertThat(plan.roleCode()).isEqualTo("manager");
    }

    @Test
    @DisplayName("position mapped role code takes priority over adminRoleCodes")
    void planUserSync_positionMapping_priorityOverLegacyCodes() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "投标管理员", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                "bid-Team",
                Set.of("boss"),
                Set.of("manager"),
                "/bidAdmin"
        );

        assertThat(plan.roleCode()).isEqualTo("/bidAdmin");
    }

    @Test
    @DisplayName("falls back to legacy role mapping when position mapping is null")
    void planUserSync_nullPositionMapping_fallsBackToLegacy() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "manager", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                "bid-Team",
                Set.of(),
                Set.of("manager"),
                null
        );

        assertThat(plan.roleCode()).isEqualTo("manager");
    }

    @Test
    @DisplayName("falls back to legacy role mapping when position mapping is blank")
    void planUserSync_blankPositionMapping_fallsBackToLegacy() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "manager", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                "bid-Team",
                Set.of(),
                Set.of("manager"),
                ""
        );

        assertThat(plan.roleCode()).isEqualTo("manager");
    }

    @Test
    @DisplayName("admin upgrade guard still blocks non-admin existing users from becoming admin via position mapping")
    void planUserSync_positionMapping_adminUpgradeGuard() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "系统管理员", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                "manager",
                Set.of(),
                Set.of(),
                "admin"
        );

        assertThat(plan.roleCode()).isEqualTo("manager");
    }

    @Test
    @DisplayName("person mapping can elevate non-admin existing user to admin when explicitly allowed")
    void planUserSync_personMapping_allowedAdminElevation() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "投标管理员", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                "manager",
                Set.of(),
                Set.of(),
                "admin",
                true
        );

        assertThat(plan.roleCode()).isEqualTo("admin");
    }

    @Test
    @DisplayName("position mapping can assign bid_specialist to new user when no existing role")
    void planUserSync_positionMapping_newUser() {
        OrganizationUserSnapshot snapshot = new OrganizationUserSnapshot(
                "10001", "zhangsan", "张三", "zhangsan@example.com",
                "13800000000", "sales", "销售部", "", "投标专员", true
        );

        OrganizationUserSyncPlan plan = OrganizationSyncPolicy.planUserSync(
                snapshot,
                null,
                Set.of(),
                Set.of(),
                "bid-Team"
        );

        assertThat(plan.roleCode()).isEqualTo("bid-Team");
    }
}
