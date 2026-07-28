// Output: NotificationRecipientResolver 4 个方法的分支覆盖（含降级）
// Pos: notification/core/ - 接收人解析器测试
package com.xiyu.bid.notification.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.service.ProjectNotificationRecipientPolicy;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessFilter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationRecipientResolver — 接收人解析 4 方法（A/C/D 组复用）")
class NotificationRecipientResolverTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private ProjectMemberRepository projectMemberRepository;
    @Mock
    private ProjectAccessFilter projectAccessFilter;
    @Mock
    private ProjectNotificationRecipientPolicy projectRecipientPolicy;

    private NotificationRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new NotificationRecipientResolver(userRepository, projectMemberRepository, projectAccessFilter, projectRecipientPolicy);
    }

    @Test
    @DisplayName("getAdminUserIds：返回 admin/bidAdmin/bid-TeamLeague 启用用户 ID")
    void getAdminUserIds_ReturnsEnabledAdminIds() {
        when(userRepository.findEnabledByRoleProfileCodes(List.copyOf(RoleProfileCatalog.GLOBAL_ACCESS_ROLES)))
                .thenReturn(List.of(user(1L), user(2L)));

        List<Long> result = resolver.getAdminUserIds();

        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("getAdminUserIds：DB 异常时降级返回空列表")
    void getAdminUserIds_DegradestoEmptyOnException() {
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenThrow(new RuntimeException("db down"));

        List<Long> result = resolver.getAdminUserIds();

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("getUserIdsByRoleCodes：空集合返回空列表")
    void getUserIdsByRoleCodes_EmptyInputReturnsEmpty() {
        assertThat(resolver.getUserIdsByRoleCodes(List.of())).isEmpty();
        assertThat(resolver.getUserIdsByRoleCodes(null)).isEmpty();
    }

    @Test
    @DisplayName("resolveProjectRecipients：委托给 ProjectNotificationRecipientPolicy 三参数方法")
    void resolveProjectRecipients_delegatesToPolicy() {
        Set<ProjectNotificationRole> roles = Set.of(
                ProjectNotificationRole.BID_LEAD,
                ProjectNotificationRole.PROJECT_OWNER
        );
        when(projectRecipientPolicy.resolveRecipients(100L, roles, 2L))
                .thenReturn(List.of(1L, 3L));

        List<Long> result = resolver.resolveProjectRecipients(100L, roles, 2L);

        assertThat(result).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("resolveProjectRecipients：支持任务执行人四参数委托")
    void resolveProjectRecipients_withTaskExecutor_delegatesToPolicy() {
        Set<ProjectNotificationRole> roles = Set.of(
                ProjectNotificationRole.TASK_EXECUTOR
        );
        when(projectRecipientPolicy.resolveRecipients(100L, roles, 2L, 8L))
                .thenReturn(List.of(8L));

        List<Long> result = resolver.resolveProjectRecipients(100L, roles, 2L, 8L);

        assertThat(result).containsExactly(8L);
    }

    @Test
    @DisplayName("getProjectMemberUserIds：返回项目成员，排除指定用户")
    void getProjectMemberUserIds_ReturnsMembersExcludingSpecified() {
        when(projectMemberRepository.findByProjectId(100L))
                .thenReturn(List.of(member(1L), member(2L), member(3L)));

        List<Long> result = resolver.getProjectMemberUserIds(100L, 2L);

        assertThat(result).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("getProjectMemberUserIds：excludeUserId=null 时不排除任何人")
    void getProjectMemberUserIds_NullExcludeKeepsAll() {
        when(projectMemberRepository.findByProjectId(100L))
                .thenReturn(List.of(member(1L), member(2L)));

        List<Long> result = resolver.getProjectMemberUserIds(100L, null);

        assertThat(result).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("getProjectMemberUserIds：DB 异常时降级返回空列表")
    void getProjectMemberUserIds_DegradesToEmptyOnException() {
        when(projectMemberRepository.findByProjectId(100L))
                .thenThrow(new RuntimeException("db down"));

        List<Long> result = resolver.getProjectMemberUserIds(100L, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("filterByProjectAccess：委托 filterUsersByProjectAccess 批量过滤候选接收人")
    void filterByProjectAccess_FiltersByAccessibility() {
        when(projectAccessFilter.filterUsersByProjectAccess(List.of(1L, 2L, 3L), 100L))
                .thenReturn(new java.util.LinkedHashSet<>(List.of(1L, 3L)));

        List<Long> result = resolver.filterByProjectAccess(List.of(1L, 2L, 3L), 100L);

        assertThat(result).containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("filterByProjectAccess：空候选集合返回空列表")
    void filterByProjectAccess_EmptyInputReturnsEmpty() {
        assertThat(resolver.filterByProjectAccess(List.of(), 100L)).isEmpty();
        assertThat(resolver.filterByProjectAccess(null, 100L)).isEmpty();
    }

    @Test
    @DisplayName("filterByProjectAccess：DB 异常时降级返回原候选集合（通知送达优先）")
    void filterByProjectAccess_DegradesToUnfilteredOnException() {
        when(projectAccessFilter.filterUsersByProjectAccess(any(), eq(100L)))
                .thenThrow(new RuntimeException("db down"));

        List<Long> result = resolver.filterByProjectAccess(List.of(1L, 2L), 100L);

        // 降级为原候选广播（符合 Constitution VII §2）
        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    private ProjectMember member(Long userId) {
        ProjectMember m = new ProjectMember();
        m.setUserId(userId);
        return m;
    }
}
