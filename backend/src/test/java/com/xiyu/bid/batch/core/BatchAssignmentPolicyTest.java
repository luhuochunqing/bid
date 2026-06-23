package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.BiFunction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BatchAssignmentPolicyTest {

    @SuppressWarnings("unchecked")
    private static final BiFunction<User, String, List<String>> SUPPLIER = mock(BiFunction.class);

    @Test
    void resolveDepartmentAssignment_RejectsUnauthorizedDepartment() {
        User currentUser = User.builder()
                .id(1L)
                .role(User.Role.MANAGER)
                .departmentCode("D1")
                .build();
        when(SUPPLIER.apply(currentUser, "READ")).thenReturn(List.of("D1"));

        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .assigneeDeptCode("D2")
                .allowCrossDeptCollaboration(false)
                .build();

        assertThatThrownBy(() -> BatchAssignmentPolicy.resolveDepartmentAssignment(request, currentUser, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("无权向该部门分配任务");
    }

    @Test
    void resolveUserAssignment_ReturnsSnapshotForEnabledUser() {
        User currentUser = User.builder()
                .id(1L)
                .role(User.Role.ADMIN)
                .build();
        User assignee = User.builder()
                .id(2L)
                .enabled(true)
                .departmentCode("D2")
                .departmentName("Dept 2")
                .role(User.Role.MANAGER)
                .build();

        BatchAssignmentSnapshot snapshot = BatchAssignmentPolicy.resolveUserAssignment(assignee, currentUser, false, SUPPLIER);

        assertThat(snapshot.assigneeId()).isEqualTo(2L);
        assertThat(snapshot.assigneeDeptCode()).isEqualTo("D2");
    }

    @Test
    void resolveUserAssignment_RejectsDisabledAssignee() {
        User currentUser = User.builder().id(1L).role(User.Role.ADMIN).build();
        User assignee = User.builder().id(2L).enabled(false).build();

        assertThatThrownBy(() -> BatchAssignmentPolicy.resolveUserAssignment(assignee, currentUser, false, SUPPLIER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("目标责任人已停用");
    }
}
