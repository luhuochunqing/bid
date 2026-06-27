package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.repository.ProjectLeadAssignmentRepository;
import com.xiyu.bid.security.CurrentUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * {@link TaskPermissionGuard} 单元测试。
 *
 * <p>核心验证（CO-373 根因修复）：OSS 用户（缓存角色 bid-Team）调用 canManageTask 不再被拒。
 * 此前 TaskPermissionGuard 直调 {@code currentUser.getRoleCode()} 读到 "manager"（OSS 用户
 * role_id=NULL 实体回退值）被拒。改造后通过 {@link CurrentUserResolver#resolveEffectiveRoleCode}
 * 走 OSS 缓存优先的统一入口。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskPermissionGuard 任务权限守卫")
class TaskPermissionGuardTest {

    @Mock
    private CurrentUserResolver currentUserResolver;

    @Mock
    private ProjectLeadAssignmentRepository projectLeadAssignmentRepository;

    private TaskPermissionGuard guard;

    @BeforeEach
    void setUp() {
        guard = new TaskPermissionGuard(currentUserResolver, projectLeadAssignmentRepository);
    }

    private User userWithId(Long id) {
        return User.builder().id(id).username("user" + id).build();
    }

    private void stubLeads(Long projectId, Long leaderId, Long deputyId) {
        when(projectLeadAssignmentRepository.resolveLeadIdsByProjectId(projectId))
            .thenReturn(new Long[]{leaderId, deputyId});
    }

    @Nested
    @DisplayName("CO-373 核心修复：OSS 用户缓存角色 bid-Team 不再被误拒")

    class OssUserWithCacheHit {

        @Test
        @DisplayName("OSS 用户缓存角色 bid-Team 作为投标负责人可管理任务（不再 403）")
        void ossUserBidTeamAsLeaderCanManage() {
            Long projectId = 100L;
            User currentUser = userWithId(501L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            // 统一入口返回 OSS 缓存角色 bid-Team（此前直调 getRoleCode() 返回 "manager" 被拒）
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("bid-Team");
            stubLeads(projectId, 501L, null);

            assertThatCode(() -> guard.assertCanManageTask(projectId))
                .as("OSS 投标负责人（缓存角色 bid-Team）应可管理任务，不应 403")
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("OSS 用户缓存角色 bid-TeamLeader（组长）可强制重派任务")
        void ossUserBidTeamLeaderCanForceReassign() {
            Long projectId = 100L;
            User currentUser = userWithId(501L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("bid-TeamLeader");
            stubLeads(projectId, 501L, null);

            assertThatCode(() -> guard.assertCanForceReassign(projectId))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("OSS 用户缓存角色 bid-Team（投标专员）不可强制重派（业务规则：仅组长/管理员可）")
        void ossUserBidTeamCannotForceReassign() {
            Long projectId = 100L;
            User currentUser = userWithId(501L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("bid-Team");
            stubLeads(projectId, 501L, null);

            assertThatThrownBy(() -> guard.assertCanForceReassign(projectId))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("仅管理员/组长可强行干预任务分配");
        }

        @Test
        @DisplayName("OSS 用户缓存角色 bid-Team 作为投标辅助人员可管理任务")
        void ossUserBidTeamAsDeputyCanManage() {
            Long projectId = 100L;
            User currentUser = userWithId(502L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("bid-Team");
            stubLeads(projectId, 501L, 502L);

            assertThatCode(() -> guard.assertCanManageTask(projectId))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("回归保护：fail-closed 与本地用户行为")

    class RegressionAndFailClosed {

        @Test
        @DisplayName("OSS 用户缓存未命中 fail-closed 返回 null 时拒绝管理任务")
        void ossUserFailClosedDenied() {
            Long projectId = 100L;
            User currentUser = userWithId(501L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn(null);
            stubLeads(projectId, 501L, null);

            assertThatThrownBy(() -> guard.assertCanManageTask(projectId))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("本地用户 manager 角色且非负责人被拒（回归不变）")
        void localManagerNotLeaderDenied() {
            Long projectId = 100L;
            User currentUser = userWithId(999L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("manager");
            stubLeads(projectId, 501L, 502L);

            assertThatThrownBy(() -> guard.assertCanManageTask(projectId))
                .isInstanceOf(AccessDeniedException.class);
        }

        @Test
        @DisplayName("本地 admin 用户可管理任意项目任务（回归不变）")
        void localAdminCanManage() {
            Long projectId = 100L;
            User currentUser = userWithId(1L);
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("admin");
            stubLeads(projectId, 501L, 502L);

            assertThatCode(() -> guard.assertCanManageTask(projectId))
                .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("assertCanManageOrSubmitTask：负责人或执行人均可")

    class ManageOrSubmit {

        @Test
        @DisplayName("OSS 用户缓存角色 bid-Team 作为执行人可操作任务（即使非负责人）")
        void ossUserAsAssigneeCanSubmit() {
            Long projectId = 100L;
            User currentUser = userWithId(503L);
            Task task = Task.builder().id(10L).projectId(projectId).assigneeId(503L).build();
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn("bid-Team");
            stubLeads(projectId, 501L, 502L);

            assertThatCode(() -> guard.assertCanManageOrSubmitTask(task))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("OSS 用户缓存 fail-closed 且非执行人被拒")
        void ossFailClosedAndNotAssigneeDenied() {
            Long projectId = 100L;
            User currentUser = userWithId(503L);
            Task task = Task.builder().id(10L).projectId(projectId).assigneeId(999L).build();
            when(currentUserResolver.requireCurrentUser()).thenReturn(currentUser);
            when(currentUserResolver.resolveEffectiveRoleCode(currentUser)).thenReturn(null);
            stubLeads(projectId, 501L, 502L);

            assertThatThrownBy(() -> guard.assertCanManageOrSubmitTask(task))
                .isInstanceOf(AccessDeniedException.class);
        }
    }
}
