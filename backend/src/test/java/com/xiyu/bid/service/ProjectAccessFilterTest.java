// Output: ProjectAccessFilter 5 路径覆盖（空集合短路 / 批量加载 / 顺序保持 / 权限判定委托 / 异常降级）
// Pos: test/java/.../service - 批量项目访问过滤器单测
// 覆盖背景: CO-599 / Spec 030 拆分出 ProjectAccessFilter 后无直接单测，
// 仅 NotificationRecipientResolverTest mock 整个过滤器。
// 补齐后锁定五条业务契约:
//   1) 空集合短路（null/empty → 空集，不查 DB）
//   2) N+1 消除：findAllById 批量加载候选用户（P0 性能契约）
//   3) 顺序保持（输出 LinkedHashSet 与输入顺序一致）
//   4) 权限判定委托给 ProjectAccessScopeService.canAccessProjectInternal
//   5) 异常降级：DB 故障时返回原候选集合（Constitution VII §2 装饰性操作失败降级）
package com.xiyu.bid.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectAccessFilter — Spec 030 批量项目访问过滤（5 路径契约）")
class ProjectAccessFilterTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProjectAccessScopeService projectAccessScopeService;

    private ProjectAccessFilter filter;

    @BeforeEach
    void setUp() {
        filter = new ProjectAccessFilter(userRepository, projectAccessScopeService);
    }

    // ============== 1. 空集合短路 ==============

    @Test
    @DisplayName("filterUsersByProjectAccess: null userIds 返回空集，不查 DB")
    void filter_NullUserIds_ReturnsEmpty() {
        Set<Long> result = filter.filterUsersByProjectAccess(null, 100L);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findAllById(any());
        verify(projectAccessScopeService, never()).canAccessProjectInternal(any(), anyLong());
    }

    @Test
    @DisplayName("filterUsersByProjectAccess: empty userIds 返回空集，不查 DB")
    void filter_EmptyUserIds_ReturnsEmpty() {
        Set<Long> result = filter.filterUsersByProjectAccess(Set.of(), 100L);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("filterUsersByProjectAccess: null projectId 返回空集，不查 DB")
    void filter_NullProjectId_ReturnsEmpty() {
        Set<Long> result = filter.filterUsersByProjectAccess(List.of(1L, 2L), null);

        assertThat(result).isEmpty();
        verify(userRepository, never()).findAllById(any());
    }

    // ============== 2. N+1 消除 (P0 性能契约) ==============

    @Test
    @DisplayName("filterUsersByProjectAccess: N 个候选用户只调一次 findAllById（消除 N+1）")
    void filter_BatchLoadsUsers_SingleDbCall() {
        when(userRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(user(1L), user(2L), user(3L)));
        when(projectAccessScopeService.canAccessProjectInternal(any(), eq(100L)))
                .thenReturn(true, false, true);

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(1L, 2L, 3L), 100L);

        // 关键契约：只调一次 findAllById（不是 3 次 findById）
        verify(userRepository).findAllById(List.of(1L, 2L, 3L));
        // 但 canAccessProjectInternal 仍然为每个候选调用一次（in-memory 判定）
        assertThat(result).containsExactlyInAnyOrder(1L, 3L);
    }

    // ============== 3. 顺序保持 ==============

    @Test
    @DisplayName("filterUsersByProjectAccess: 输出 LinkedHashSet 与输入顺序一致（保序契约）")
    void filter_PreservesInputOrder() {
        when(userRepository.findAllById(List.of(5L, 3L, 7L, 1L)))
                .thenReturn(List.of(user(5L), user(3L), user(7L), user(1L)));
        // 全部允许访问
        when(projectAccessScopeService.canAccessProjectInternal(any(), eq(100L)))
                .thenReturn(true);

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(5L, 3L, 7L, 1L), 100L);

        // 输出顺序 = 输入顺序 (LinkedHashSet 保序)
        assertThat(result).containsExactly(5L, 3L, 7L, 1L);
        assertThat(result).isInstanceOf(java.util.LinkedHashSet.class);
    }

    // ============== 4. 权限判定委托 ==============

    @Test
    @DisplayName("filterUsersByProjectAccess: 权限判定委托给 canAccessProjectInternal (Package-private 共享核心)")
    void filter_DelegatesAccessCheckToCanAccessProjectInternal() {
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(user(1L), user(2L)));
        // 模拟: 1L 可访问, 2L 不可访问
        when(projectAccessScopeService.canAccessProjectInternal(user(1L), 100L)).thenReturn(true);
        when(projectAccessScopeService.canAccessProjectInternal(user(2L), 100L)).thenReturn(false);

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(1L, 2L), 100L);

        // 必须走包内共享核心 canAccessProjectInternal（与 ProjectAccessScopeService 入口同源）
        verify(projectAccessScopeService).canAccessProjectInternal(user(1L), 100L);
        verify(projectAccessScopeService).canAccessProjectInternal(user(2L), 100L);
        assertThat(result).containsExactly(1L);
    }

    @Test
    @DisplayName("filterUsersByProjectAccess: 候选 ID 在 DB 中查不到 (findAllById 缺失) 时被排除")
    void filter_CandidateNotInDb_Excluded() {
        when(userRepository.findAllById(List.of(1L, 2L, 999L)))
                .thenReturn(List.of(user(1L), user(2L))); // 999L 在 DB 中不存在
        when(projectAccessScopeService.canAccessProjectInternal(any(), eq(100L)))
                .thenReturn(true);

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(1L, 2L, 999L), 100L);

        // 999L 因 user=null 被排除（避免 NPE）
        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
        assertThat(result).doesNotContain(999L);
    }

    // ============== 5. 异常降级 (Constitution VII §2) ==============

    @Test
    @DisplayName("filterUsersByProjectAccess: DB 异常时降级返回原候选集合（通知送达优先）")
    void filter_DbException_DegradesToUnfilteredInput() {
        when(userRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenThrow(new RuntimeException("db connection lost"));

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(1L, 2L, 3L), 100L);

        // 异常时降级为原候选广播，优先保证通知送达（Constitution VII §2）
        assertThat(result).containsExactlyInAnyOrder(1L, 2L, 3L);
        // 异常时不应进入 canAccessProjectInternal 判定
        verify(projectAccessScopeService, never()).canAccessProjectInternal(any(), anyLong());
    }

    @Test
    @DisplayName("filterUsersByProjectAccess: canAccessProjectInternal 抛异常时降级返回原候选集合")
    void filter_AccessCheckException_DegradesToUnfilteredInput() {
        when(userRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(user(1L), user(2L)));
        when(projectAccessScopeService.canAccessProjectInternal(any(), anyLong()))
                .thenThrow(new RuntimeException("permission service down"));

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(1L, 2L), 100L);

        // 即使权限服务故障，也降级为原候选集合（不阻塞通知派发）
        assertThat(result).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    @DisplayName("filterUsersByProjectAccess: 异常降级时仍保持输入顺序 (LinkedHashSet)")
    void filter_DegradedResult_PreservesInputOrder() {
        when(userRepository.findAllById(List.of(3L, 1L, 2L)))
                .thenThrow(new RuntimeException("db down"));

        Set<Long> result = filter.filterUsersByProjectAccess(List.of(3L, 1L, 2L), 100L);

        // 降级时仍按输入顺序输出
        assertThat(result).containsExactly(3L, 1L, 2L);
    }

    // ============== 测试辅助 ==============

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }
}
