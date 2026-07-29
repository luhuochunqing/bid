// Output: TaskReminderRecipientResolver 4 路径覆盖（resolve/loadEnabledUserIdsByRoleCode/preloadGlobalBroadcastIds/GlobalBroadcastIds）
// Pos: test/java/.../task/reminder - 接收人解析器单测
// 覆盖背景: CO-599 从 TaskDueReminderService 拆出后无直接单测，仅 TaskDueReminderServiceTest mock 整个解析器。
// 补齐后锁定四条业务契约:
//   1) 四类接收人聚合（项目级 + 项目负责人 + 全局广播 + 升级模式）
//   2) 项目负责人启用状态校验（禁用用户不接收通知）
//   3) 升级模式仅在 overdueMode 时追加 /bidAdmin 全局广播
//   4) 全局广播角色用户在扫描顶部预查一次（避免 N 次重复查询的 P0 性能契约）
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.task.reminder.TaskReminderRecipientResolver.GlobalBroadcastIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TaskReminderRecipientResolver — 接收人解析 3 路径（CO-599 拆分回归守卫）")
class TaskReminderRecipientResolverTest {

    @Mock
    private NotificationRecipientResolver recipientResolver;

    private TaskReminderRecipientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new TaskReminderRecipientResolver(recipientResolver);
    }

    // ============== resolve() — 四类接收人聚合 ==============

    @Test
    @DisplayName("resolve: 普通模式 = 项目级(3 角色) + 项目负责人(启用) + 投标组长全局广播")
    void resolve_NormalMode_AggregatesFourStreams() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(200L);
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(200L, user(200L, true));

        // 1) 项目级：返回 BID_LEAD + BID_ASSISTANT + TASK_EXECUTOR 解析结果
        when(recipientResolver.resolveAndFilterProjectRecipients(
                eq(20L),
                eq(Set.of(ProjectNotificationRole.BID_LEAD,
                        ProjectNotificationRole.BID_ASSISTANT,
                        ProjectNotificationRole.TASK_EXECUTOR)),
                eq(null),
                eq(50L)))
                .thenReturn(List.of(101L, 102L, 50L));

        List<Long> result = resolver.resolve(task, project, false, userCache,
                Set.of(103L), Set.of());

        // 期望: 项目级(101/102/50) + 项目负责人(200) + 投标组长(103) = 5 个
        assertThat(result).containsExactlyInAnyOrder(101L, 102L, 50L, 200L, 103L);
    }

    @Test
    @DisplayName("resolve: 升级模式 (escalate=true) 追加 /bidAdmin 全局广播，不做项目过滤")
    void resolve_EscalateMode_AppendsBidAdminIds() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(200L);
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(200L, user(200L, true));

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L));

        List<Long> result = resolver.resolve(task, project, true, userCache,
                Set.of(103L), Set.of(104L));

        // 升级模式: 项目级(101) + 项目负责人(200) + 投标组长(103) + /bidAdmin(104) = 4 个
        assertThat(result).containsExactlyInAnyOrder(101L, 200L, 103L, 104L);
    }

    @Test
    @DisplayName("resolve: 升级模式 (escalate=false) 不追加 /bidAdmin，避免非逾期项目误广播管理员")
    void resolve_NonEscalate_DoesNotIncludeBidAdmin() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(200L);
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(200L, user(200L, true));

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L));

        List<Long> result = resolver.resolve(task, project, false, userCache,
                Set.of(103L), Set.of(104L));

        // 非升级模式: 传入的 bidAdminIds(104) 应当被忽略
        assertThat(result).containsExactlyInAnyOrder(101L, 200L, 103L);
        assertThat(result).doesNotContain(104L);
    }

    @Test
    @DisplayName("resolve: project=null 时跳过项目负责人校验；project.managerId=null 时不加入负责人")
    void resolve_NullProjectOrManager_HandlesGracefully() {
        Task task = sampleTask(20L, 50L);
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(999L, user(999L, true));

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L));

        // (a) project=null
        List<Long> resultNullProject = resolver.resolve(task, null, false, userCache,
                Set.of(103L), Set.of());
        assertThat(resultNullProject).containsExactlyInAnyOrder(101L, 103L);

        // (b) project.managerId=null
        Project projectWithoutManager = new Project();
        projectWithoutManager.setId(20L);
        // managerId 默认 null
        List<Long> resultNullManager = resolver.resolve(task, projectWithoutManager, false, userCache,
                Set.of(103L), Set.of());
        assertThat(resultNullManager).containsExactlyInAnyOrder(101L, 103L);
    }

    @Test
    @DisplayName("resolve: 项目负责人被禁用 (enabled=false) 时不接收通知（业务契约：禁用用户不接收通知）")
    void resolve_DisabledManager_ExcludedFromRecipients() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(200L);
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(200L, user(200L, false)); // 禁用

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L));

        List<Long> result = resolver.resolve(task, project, false, userCache,
                Set.of(103L), Set.of());

        // 禁用的项目负责人 200 不在结果中
        assertThat(result).containsExactlyInAnyOrder(101L, 103L);
        assertThat(result).doesNotContain(200L);
    }

    @Test
    @DisplayName("resolve: 项目负责人未在 userCache 时 (managerId 存在但查不到) 跳过")
    void resolve_ManagerNotInCache_Excluded() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(200L);
        Map<Long, User> userCache = new HashMap<>(); // 空 cache

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L));

        List<Long> result = resolver.resolve(task, project, false, userCache,
                Set.of(103L), Set.of());

        // cache 缺失 → 不抛 NPE，managerId 不被加入
        assertThat(result).containsExactlyInAnyOrder(101L, 103L);
    }

    @Test
    @DisplayName("resolve: 接收人去重（LinkedHashSet 语义）— 同 ID 在多流中出现时只保留一次")
    void resolve_DeduplicatesAcrossStreams() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(103L); // 103 既是项目负责人也是投标组长
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(103L, user(103L, true));

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L, 103L)); // 项目级也包含 103

        List<Long> result = resolver.resolve(task, project, false, userCache,
                Set.of(103L), Set.of()); // 全局广播也包含 103

        // 103 应当只出现一次
        assertThat(result).containsExactly(101L, 103L);
        assertThat(result.stream().filter(id -> id == 103L).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("resolve: 全局广播空集合 (edge case) — 仍返回项目级 + 项目负责人")
    void resolve_EmptyGlobalBroadcast_ReturnsProjectAndManagerOnly() {
        Task task = sampleTask(20L, 50L);
        Project project = projectWithManager(200L);
        Map<Long, User> userCache = new HashMap<>();
        userCache.put(200L, user(200L, true));

        when(recipientResolver.resolveAndFilterProjectRecipients(anyLong(), any(), any(), anyLong()))
                .thenReturn(List.of(101L));

        List<Long> result = resolver.resolve(task, project, false, userCache,
                Set.of(), Set.of());

        assertThat(result).containsExactlyInAnyOrder(101L, 200L);
    }

    // ============== loadEnabledUserIdsByRoleCode() ==============

    @Test
    @DisplayName("loadEnabledUserIdsByRoleCode: null roleCode 返回空集，不触发下游查询")
    void loadEnabledUserIdsByRoleCode_NullInput_ReturnsEmpty() {
        Set<Long> result = resolver.loadEnabledUserIdsByRoleCode(null);

        assertThat(result).isEmpty();
        verify(recipientResolver, never()).getUserIdsByRoleCodes(any());
    }

    @Test
    @DisplayName("loadEnabledUserIdsByRoleCode: 非 null roleCode 委托下游并转为 LinkedHashSet 保持顺序")
    void loadEnabledUserIdsByRoleCode_DelegatesAndPreservesOrder() {
        when(recipientResolver.getUserIdsByRoleCodes(Set.of("bid-TeamLeader")))
                .thenReturn(List.of(103L, 105L, 102L));

        Set<Long> result = resolver.loadEnabledUserIdsByRoleCode("bid-TeamLeader");

        assertThat(result).containsExactly(103L, 105L, 102L);
        // 验证确实是 LinkedHashSet（保序）
        assertThat(result).isInstanceOf(java.util.LinkedHashSet.class);
    }

    // ============== preloadGlobalBroadcastIds() ==============

    @Test
    @DisplayName("preloadGlobalBroadcastIds: 非逾期模式只预查 bid-TeamLeader，不预查 /bidAdmin")
    void preloadGlobalBroadcastIds_NonOverdueMode_OnlyTeamLeader() {
        when(recipientResolver.getUserIdsByRoleCodes(Set.of(RoleProfileCatalog.BID_LEAD_CODE)))
                .thenReturn(List.of(103L));

        GlobalBroadcastIds result = resolver.preloadGlobalBroadcastIds(false);

        assertThat(result.teamLeaderIds()).containsExactly(103L);
        assertThat(result.bidAdminIds()).isEmpty();
        // 关键：未触发 /bidAdmin 查询（P0 性能契约：避免循环内 N 次重复查询）
        verify(recipientResolver, never()).getUserIdsByRoleCodes(Set.of(RoleProfileCatalog.BID_ADMIN_CODE));
    }

    @Test
    @DisplayName("preloadGlobalBroadcastIds: 逾期模式同时预查 bid-TeamLeader + /bidAdmin")
    void preloadGlobalBroadcastIds_OverdueMode_LoadsBoth() {
        when(recipientResolver.getUserIdsByRoleCodes(Set.of(RoleProfileCatalog.BID_LEAD_CODE)))
                .thenReturn(List.of(103L));
        when(recipientResolver.getUserIdsByRoleCodes(Set.of(RoleProfileCatalog.BID_ADMIN_CODE)))
                .thenReturn(List.of(104L));

        GlobalBroadcastIds result = resolver.preloadGlobalBroadcastIds(true);

        assertThat(result.teamLeaderIds()).containsExactly(103L);
        assertThat(result.bidAdminIds()).containsExactly(104L);
    }

    // ============== GlobalBroadcastIds record ==============

    @Test
    @DisplayName("GlobalBroadcastIds record: 构造后两字段可独立访问（基础契约）")
    void globalBroadcastIds_Record_FieldsAccessible() {
        GlobalBroadcastIds ids = new GlobalBroadcastIds(Set.of(103L), Set.of(104L));

        assertThat(ids.teamLeaderIds()).containsExactly(103L);
        assertThat(ids.bidAdminIds()).containsExactly(104L);
    }

    // ============== 测试辅助 ==============

    private Task sampleTask(Long projectId, Long assigneeId) {
        Task task = new Task();
        task.setId(10L);
        task.setProjectId(projectId);
        task.setAssigneeId(assigneeId);
        return task;
    }

    private Project projectWithManager(Long managerId) {
        Project project = new Project();
        project.setId(20L);
        project.setManagerId(managerId);
        return project;
    }

    private User user(Long id, Boolean enabled) {
        User u = new User();
        u.setId(id);
        u.setEnabled(enabled);
        return u;
    }
}
