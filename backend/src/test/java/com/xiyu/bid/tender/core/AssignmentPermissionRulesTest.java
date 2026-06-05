package com.xiyu.bid.tender.core;

import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentPermissionRulesTest {

    @Test
    @DisplayName("canEditTender: admin role -> true")
    void canEditTender_admin_returnsTrue() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "admin", User.Role.ADMIN, 1L, 2L, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canEditTender: manager role -> true")
    void canEditTender_manager_returnsTrue() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "manager", User.Role.MANAGER, 1L, 2L, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canEditTender: bid_specialist, unassigned and self-created -> true (creator can edit PENDING_ASSIGNMENT)")
    void canEditTender_bidSpecialist_unassigned_selfCreated_returnsTrue() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "bid_specialist", User.Role.STAFF, 1L, 1L, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canEditTender: bid_specialist, assigned and self-created -> true")
    void canEditTender_bidSpecialist_assigned_selfCreated_returnsTrue() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "bid_specialist", User.Role.STAFF, 1L, 1L, Tender.Status.TRACKING);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canEditTender: bid_specialist, unassigned and other-created -> false")
    void canEditTender_bidSpecialist_unassigned_otherCreated_returnsFalse() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "bid_specialist", User.Role.STAFF, 1L, 2L, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEditTender: bid_specialist, assigned and other-created -> false")
    void canEditTender_bidSpecialist_assigned_otherCreated_returnsFalse() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "bid_specialist", User.Role.STAFF, 1L, 2L, Tender.Status.TRACKING);
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canEditTender: task_executor (other staff role), self-created PENDING_ASSIGNMENT -> true")
    void canEditTender_taskExecutor_selfCreated_returnsTrue() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "task_executor", User.Role.STAFF, 1L, 1L, Tender.Status.PENDING_ASSIGNMENT);
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canEditTender: null userId -> false")
    void canEditTender_nullUserId_returnsFalse() {
        boolean result = AssignmentPermissionRules.canEditTender(
                "bid_specialist", User.Role.STAFF, null, 1L, Tender.Status.TRACKING);
        assertThat(result).isFalse();
    }
}
