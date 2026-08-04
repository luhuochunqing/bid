package com.xiyu.bid.notification.service;

import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.matrixcollaboration.entity.ProjectMember;
import com.xiyu.bid.matrixcollaboration.repository.ProjectMemberRepository;
import com.xiyu.bid.notification.core.NotificationRecipientFilter;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * 通知接收人解析器 — 收敛"按角色/项目解析接收人"的通用逻辑。
 *
 * <p>本类消除项目中散落的接收人解析重复（A/C/D 组）：
 * <ul>
 *   <li>A 组：{@code getAdminUserIds()}（admin/bidAdmin/bid-TeamLeader 三连码，
 *       原散落在 ProjectNotificationService / ProjectClosureService / ProjectRetrospectiveService）</li>
 *   <li>C 组：{@code getProjectMemberUserIds(projectId, excludeUserId)}（项目团队成员，
 *       原散落在 ProjectNotificationService / DocumentChangeNotificationService）</li>
 *   <li>D 组：{@code filterByProjectAccess(ids, projectId)}（Spec 030 项目可见性过滤，
 *       原散落在 DocumentChangeNotificationService / TaskReviewNotificationService 的 filterRecipientsSafe）</li>
 * </ul>
 *
 * <p><b>降级策略</b>：所有方法内部 try-catch，DB 异常时降级返回空列表（或原候选集合，
 * 对 {@code filterByProjectAccess}）——符合 Constitution VII §2 "装饰性操作失败必须降级"。
 * 调用方拿到的永远是 List（永不抛异常），简化上游错误处理。</p>
 *
 * <p><b>不在本类范围</b>：B 组（按角色码的通用解析，9 处跨 7 模块）作为独立后续 PR。
 * {@code ProjectNotificationService.getProjectLeadIds}（依赖 ProjectLeadAssignmentRepository
 * 的专有组合查询）保留在原服务内。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationRecipientResolver {

    private final UserRepository userRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final ProjectAccessFilter projectAccessFilter;
    private final ProjectNotificationRecipientPolicy projectRecipientPolicy;

    /**
     * 解析项目管理员用户 ID 列表（bidAdmin/bid-TeamLeader/bid-SystemAdmin，排除本地 admin 超级管理员）。
     *
     * <p>对应原 A 组重复：三处 service 私有方法完全复制粘贴的逻辑。</p>
     *
     * @return 启用状态的管理员用户 ID 列表；查询异常时返回空列表
     */
    public List<Long> getAdminUserIds() {
        return getUserIdsByRoleCodes(RoleProfileCatalog.NOTIFICATION_RECIPIENT_ROLES);
    }

    /**
     * 通用：按角色码集合解析启用的用户 ID 列表。
     *
     * <p>供 B 组后续 PR 复用；本 PR 内由 {@link #getAdminUserIds()} 调用。</p>
     *
     * @param roleCodes 角色码集合（可为空，返回空列表）
     * @return 启用状态的用户 ID 列表；查询异常时返回空列表
     */
    public List<Long> getUserIdsByRoleCodes(Collection<String> roleCodes) {
        if (roleCodes == null || roleCodes.isEmpty()) {
            return List.of();
        }
        try {
            return userRepository.findEnabledByRoleProfileCodes(List.copyOf(roleCodes))
                    .stream()
                    .map(User::getId)
                    .toList();
        } catch (RuntimeException e) {
            log.warn("getUserIdsByRoleCodes failed for codes={}, returning empty list: {}",
                    roleCodes, e.getMessage());
            return List.of();
        }
    }

    /**
     * 按项目角色解析接收人用户 ID 列表。
     *
     * <p>委托给 {@link ProjectNotificationRecipientPolicy}，收敛项目维度通知接收人解析逻辑。</p>
     *
     * @param projectId     项目 ID
     * @param roles         目标项目角色集合
     * @param excludeUserId 要排除的用户 ID（通常为操作人自己，可为 null）
     * @return 去重、保序后的接收人用户 ID 列表
     */
    public List<Long> resolveProjectRecipients(Long projectId, Set<ProjectNotificationRole> roles, Long excludeUserId) {
        return projectRecipientPolicy.resolveRecipients(projectId, roles, excludeUserId);
    }

    /**
     * 按项目角色解析接收人用户 ID 列表（支持任务执行人）。
     *
     * @param projectId      项目 ID
     * @param roles          目标项目角色集合
     * @param excludeUserId  要排除的用户 ID（通常为操作人自己，可为 null）
     * @param taskExecutorId 任务执行人用户 ID（仅在 roles 包含 TASK_EXECUTOR 时使用，可为 null）
     * @return 去重、保序后的接收人用户 ID 列表
     */
    public List<Long> resolveProjectRecipients(Long projectId, Set<ProjectNotificationRole> roles, Long excludeUserId, Long taskExecutorId) {
        return projectRecipientPolicy.resolveRecipients(projectId, roles, excludeUserId, taskExecutorId);
    }

    /**
     * 按项目角色解析接收人并执行项目可见性过滤（Spec 030）。
     *
     * <p>把 {@link #resolveProjectRecipients} 与 {@link #filterByProjectAccess} 内聚为一步，
     * 避免每个调用方重复编写过滤逻辑。</p>
     *
     * @param projectId     项目 ID
     * @param roles         目标项目角色集合
     * @param excludeUserId 要排除的用户 ID（通常为操作人自己，可为 null）
     * @return 有项目访问权的接收人用户 ID 列表
     */
    public List<Long> resolveAndFilterProjectRecipients(Long projectId, Set<ProjectNotificationRole> roles, Long excludeUserId) {
        return filterByProjectAccess(resolveProjectRecipients(projectId, roles, excludeUserId), projectId);
    }

    /**
     * 按项目角色解析接收人并执行项目可见性过滤（支持任务执行人角色）。
     *
     * @param projectId      项目 ID
     * @param roles          目标项目角色集合
     * @param excludeUserId  要排除的用户 ID（通常为操作人自己，可为 null）
     * @param taskExecutorId 任务执行人用户 ID（仅在 roles 包含 TASK_EXECUTOR 时使用，可为 null）
     * @return 有项目访问权的接收人用户 ID 列表
     */
    public List<Long> resolveAndFilterProjectRecipients(Long projectId, Set<ProjectNotificationRole> roles, Long excludeUserId, Long taskExecutorId) {
        return filterByProjectAccess(resolveProjectRecipients(projectId, roles, excludeUserId, taskExecutorId), projectId);
    }

    /**
     * 解析项目团队成员用户 ID 列表，可选排除指定用户。
     *
     * <p>对应原 C 组重复：ProjectNotificationService.getProjectTeamMemberIds 和
     * DocumentChangeNotificationService 内联成员解析。</p>
     *
     * @param projectId     项目 ID
     * @param excludeUserId 要排除的用户 ID（通常为操作人自己，可为 null 表示不排除）
     * @return 项目团队成员用户 ID 列表；查询异常时返回空列表
     */
    public List<Long> getProjectMemberUserIds(Long projectId, Long excludeUserId) {
        try {
            return projectMemberRepository.findByProjectId(projectId).stream()
                    .map(ProjectMember::getUserId)
                    .filter(id -> !Objects.equals(id, excludeUserId))
                    .toList();
        } catch (RuntimeException e) {
            log.warn("getProjectMemberUserIds failed for project={}, returning empty list: {}",
                    projectId, e.getMessage());
            return List.of();
        }
    }

    /**
     * Spec 030：按项目可见性过滤候选接收人，剔除对该项目无访问权的用户。
     *
     * <p>对应原 D 组重复：DocumentChangeNotificationService.filterRecipientsSafe 和
     * TaskReviewNotificationService.filterRecipientsSafe 完全复制粘贴的逻辑。</p>
     *
     * <p><b>批量优化</b>：委托给 {@link ProjectAccessScopeService#filterUsersByProjectAccess}
     * 一次性批量加载候选用户并判定，避免对每个候选 uid 单独查 {@code userRepository.findById}
     * 形成 N+1 查询。原 {@code NotificationRecipientFilter.filterRecipients} 调用已下沉到 service 层。</p>
     *
     * <p><b>降级策略</b>：当 {@link ProjectAccessScopeService#filterUsersByProjectAccess} 抛异常时
     * （DB 故障、OSS 同步异常等），返回原候选集合——优先保证通知送达而非精准。
     * 符合 Constitution VII §2 "装饰性操作失败必须降级"精神。</p>
     *
     * @param candidateIds 候选接收人用户 ID 集合
     * @param projectId    项目 ID（用于可见性判定）
     * @return 过滤后的接收人列表；过滤异常时返回原候选集合
     */
    public List<Long> filterByProjectAccess(Collection<Long> candidateIds, Long projectId) {
        if (candidateIds == null || candidateIds.isEmpty()) {
            return List.of();
        }
        try {
            return List.copyOf(projectAccessFilter.filterUsersByProjectAccess(candidateIds, projectId));
        } catch (RuntimeException e) {
            log.warn("Recipient filter failed for project {}, falling back to unfiltered broadcast: {}",
                    projectId, e.getMessage());
            return List.copyOf(candidateIds);
        }
    }
}
