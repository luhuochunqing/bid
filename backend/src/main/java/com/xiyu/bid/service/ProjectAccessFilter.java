// Input: UserRepository + ProjectAccessScopeService
// Output: 批量项目访问权限过滤结果
// Pos: Service/权限支撑层
// 维护声明: 从 ProjectAccessScopeService 拆出（避免主类超 300 行预算）；
//           专注"批量过滤"场景，单点判定仍走 ProjectAccessScopeService.canAccessProject。
package com.xiyu.bid.service;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spec 030 批量项目访问过滤器。
 *
 * <p>从 {@link ProjectAccessScopeService} 拆出，专注"批量过滤"场景：
 * 一次性 {@code findAllById} 加载候选用户，避免调用方循环内 N 次查 {@code userRepository.findById}。</p>
 *
 * <p>判定口径与 {@link ProjectAccessScopeService#canAccessProjectInternal} 完全一致：
 * admin/dataScope=all 短路放行，其余走 {@code getAllowedProjectIds} 比对。
 * 异常时降级返回原始候选集（保证通知送达优先，符合 Constitution VII §2）。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectAccessFilter {

    private final UserRepository userRepository;
    private final ProjectAccessScopeService projectAccessScopeService;

    /**
     * Spec 030 批量过滤：从候选用户集合中筛选出可访问指定项目的用户。
     *
     * @param userIds   候选用户 ID 集合（null/empty 返回空集）
     * @param projectId 项目 ID（null 返回空集）
     * @return 对该项目有访问权的用户 ID 集合（保持输入顺序）
     */
    @Transactional(readOnly = true)
    public Set<Long> filterUsersByProjectAccess(final Collection<Long> userIds, final Long projectId) {
        if (userIds == null || userIds.isEmpty() || projectId == null) {
            return Set.of();
        }
        try {
            // 一次 findAllById 批量加载所有候选用户，避免 N 次 findById
            List<User> users = userRepository.findAllById(userIds);
            Map<Long, User> userById = users.stream()
                    .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
            Set<Long> filtered = new LinkedHashSet<>();
            for (Long uid : userIds) {
                User user = userById.get(uid);
                if (user != null && projectAccessScopeService.canAccessProjectInternal(user, projectId)) {
                    filtered.add(uid);
                }
            }
            return filtered;
        } catch (RuntimeException e) {
            // 降级：异常时返回原始候选集，优先保证通知送达
            log.warn("filterUsersByProjectAccess failed for project {}, degrading to unfiltered: {}",
                    projectId, e.getMessage());
            return new LinkedHashSet<>(userIds);
        }
    }
}
