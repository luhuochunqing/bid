package com.xiyu.bid.tender.core;

import com.xiyu.bid.batch.entity.TenderAssignmentRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AssignmentPermissionRulesTest {

    @Test
    @DisplayName("canFill: assignee matches user -> true")
    void canFill_assigneeMatchesUser_returnsTrue() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);
        record.setAssignedById(2L);

        boolean result = AssignmentPermissionRules.canFill(Optional.of(record), 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canFill: assignee does not match user -> false")
    void canFill_assigneeDoesNotMatchUser_returnsFalse() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);
        record.setAssignedById(2L);

        boolean result = AssignmentPermissionRules.canFill(Optional.of(record), 2L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canFill: no assignment record -> false")
    void canFill_noAssignmentRecord_returnsFalse() {
        boolean result = AssignmentPermissionRules.canFill(Optional.empty(), 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canFill: null userId -> false")
    void canFill_nullUserId_returnsFalse() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);

        boolean result = AssignmentPermissionRules.canFill(Optional.of(record), null);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canDecide: assignedBy matches user -> true")
    void canDecide_assignedByMatchesUser_returnsTrue() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);
        record.setAssignedById(2L);

        boolean result = AssignmentPermissionRules.canDecide(Optional.of(record), 2L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("canDecide: assignedBy does not match user -> false")
    void canDecide_assignedByDoesNotMatchUser_returnsFalse() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);
        record.setAssignedById(2L);

        boolean result = AssignmentPermissionRules.canDecide(Optional.of(record), 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canDecide: no assignment record -> false")
    void canDecide_noAssignmentRecord_returnsFalse() {
        boolean result = AssignmentPermissionRules.canDecide(Optional.empty(), 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canDecide: null assignedBy -> false")
    void canDecide_nullAssignedBy_returnsFalse() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);
        record.setAssignedById(null);

        boolean result = AssignmentPermissionRules.canDecide(Optional.of(record), 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("canDecide: null userId -> false")
    void canDecide_nullUserId_returnsFalse() {
        TenderAssignmentRecord record = new TenderAssignmentRecord();
        record.setAssigneeId(1L);
        record.setAssignedById(1L);

        boolean result = AssignmentPermissionRules.canDecide(Optional.of(record), null);

        assertThat(result).isFalse();
    }
}
