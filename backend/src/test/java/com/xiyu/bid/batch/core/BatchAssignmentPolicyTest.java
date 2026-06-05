package com.xiyu.bid.batch.core;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.dto.TaskAssignmentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchAssignmentPolicyTest {

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private BatchAssignmentPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new BatchAssignmentPolicy(projectAccessScopeService);
    }

    @Test
    void resolveDepartmentAssignment_RejectsUnauthorizedDepartment() {
        User currentUser = User.builder()
                .id(1L)
                .role(User.Role.STAFF)
                .departmentCode("D1")
                .build();
        when(projectAccessScopeService.getAllowedDepartmentCodes(currentUser)).thenReturn(List.of("D1"));

        TaskAssignmentRequest request = TaskAssignmentRequest.builder()
                .assigneeDeptCode("D2")
                .allowCrossDeptCollaboration(false)
                .build();

        assertThatThrownBy(() -> policy.resolveDepartmentAssignment(request, currentUser))
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
                .role(User.Role.STAFF)
                .build();

        BatchAssignmentSnapshot snapshot = policy.resolveUserAssignment(assignee, currentUser, false);

        assertThat(snapshot.assigneeId()).isEqualTo(2L);
        assertThat(snapshot.assigneeDeptCode()).isEqualTo("D2");
    }
}
