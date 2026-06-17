package com.xiyu.bid.tender.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TenderTransferPermissionPolicyTest {

    @ParameterizedTest(name = "canTransfer: role={0} -> true")
    @ValueSource(strings = {"admin", "bid_admin", "bid_lead", "bid_senior"})
    @DisplayName("可转派角色返回 true")
    void canTransfer_transferableRoles_returnsTrue(String roleCode) {
        assertThat(TenderTransferPermissionPolicy.canTransfer(roleCode)).isTrue();
    }

    @ParameterizedTest(name = "canTransfer: role={0} -> false")
    @ValueSource(strings = {"sales", "bid_specialist", "task_executor", "admin_staff", "manager", "staff"})
    @DisplayName("不可转派角色返回 false")
    void canTransfer_nonTransferableRoles_returnsFalse(String roleCode) {
        assertThat(TenderTransferPermissionPolicy.canTransfer(roleCode)).isFalse();
    }

    @Test
    @DisplayName("canTransfer: null roleCode -> false")
    void canTransfer_nullRoleCode_returnsFalse() {
        assertThat(TenderTransferPermissionPolicy.canTransfer(null)).isFalse();
    }

    @Test
    @DisplayName("canTransfer: blank roleCode -> false")
    void canTransfer_blankRoleCode_returnsFalse() {
        assertThat(TenderTransferPermissionPolicy.canTransfer("   ")).isFalse();
    }

    @Test
    @DisplayName("canTransfer: unknown roleCode -> false")
    void canTransfer_unknownRoleCode_returnsFalse() {
        assertThat(TenderTransferPermissionPolicy.canTransfer("unknown_role")).isFalse();
    }

    @Test
    @DisplayName("canTransfer: roleCode 大小写不敏感且忽略前后空格")
    void canTransfer_roleCodeCaseInsensitiveAndTrimmed() {
        assertThat(TenderTransferPermissionPolicy.canTransfer("ADMIN")).isTrue();
        assertThat(TenderTransferPermissionPolicy.canTransfer("  BID_ADMIN  ")).isTrue();
        assertThat(TenderTransferPermissionPolicy.canTransfer("Bid_Lead")).isTrue();
        assertThat(TenderTransferPermissionPolicy.canTransfer("SALES")).isFalse();
    }
}
