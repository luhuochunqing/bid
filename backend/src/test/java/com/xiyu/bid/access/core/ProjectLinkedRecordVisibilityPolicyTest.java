package com.xiyu.bid.access.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectLinkedRecordVisibilityPolicyTest {

    @Test
    void visible_ShouldAllowAdminRegardlessOfProjectScope() {
        assertThat(ProjectLinkedRecordVisibilityPolicy.visible(true, List.of(), 99L)).isTrue();
    }

    @Test
    void visible_ShouldAllowSharedRecordsWithoutProjectId() {
        assertThat(ProjectLinkedRecordVisibilityPolicy.visible(false, List.of(), null)).isTrue();
    }

    @Test
    void visible_ShouldAllowOnlyRecordsInCurrentUserProjectScope() {
        assertThat(ProjectLinkedRecordVisibilityPolicy.visible(false, List.of(1L, 2L), 2L)).isTrue();
        assertThat(ProjectLinkedRecordVisibilityPolicy.visible(false, List.of(1L, 2L), 3L)).isFalse();
        assertThat(ProjectLinkedRecordVisibilityPolicy.visible(false, null, 3L)).isFalse();
    }
}
