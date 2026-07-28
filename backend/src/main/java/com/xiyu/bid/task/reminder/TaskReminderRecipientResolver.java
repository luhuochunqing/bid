// Input: Task + Project + UserCache + NotificationRecipientResolver
// Output: 任务预警接收人 ID 列表（项目级过滤 + 全局广播 + 升级模式）
// Pos: task/reminder - 接收人解析支撑层
// 维护声明:
//   - 从 TaskDueReminderService 拆出（避免主类超 300 行预算）；
//   - 接收人解析（Spec 030 项目级过滤）：
//       · 项目级：主投标负责人(BID_LEAD) + 副投标负责人(BID_ASSISTANT) + 任务执行人(TASK_EXECUTOR)
//         通过 recipientResolver.resolveAndFilterProjectRecipients 解析，叠加 filterByProjectAccess 二次过滤；
//         （bid-Team 成员被分配为投标负责人/辅助人员时仅接收本项目通知，不全局广播 bid-Team）
//       · 项目负责人：project.managerId 直接加入（启用状态校验）；
//       · 全局广播：投标组长(bid-TeamLeader) 始终全局广播（与投标管理员口径一致）；
//       · 升级模式（逾期>7天）：追加 /bidAdmin 全局广播（按业务口径不做项目过滤）；
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.ProjectNotificationRole;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CO-533 任务预警接收人解析器。
 *
 * <p>从 {@link TaskDueReminderService} 拆出，专注接收人解析职责：
 * 项目级接收人解析 + 项目负责人校验 + 全局广播角色预查 + 升级模式。</p>
 *
 * <p><b>性能设计</b>：全局广播角色用户在扫描顶部预查一次，循环内直接复用，
 * 避免 N 次重复查询同一角色用户列表。</p>
 */
@Component
@RequiredArgsConstructor
public class TaskReminderRecipientResolver {

    private final NotificationRecipientResolver recipientResolver;

    /**
     * 解析接收人（Spec 030 项目级过滤）。
     *
     * <p>四类接收人：
     * <ul>
     *   <li>项目级：主投标负责人(BID_LEAD) + 投标副负责人(BID_ASSISTANT) + 任务执行人(TASK_EXECUTOR)，
     *       通过 {@link NotificationRecipientResolver#resolveAndFilterProjectRecipients} 解析，
     *       叠加 {@code filterByProjectAccess} 二次过滤，剔除对该项目无访问权的用户。
     *       （bid-Team 成员被分配为投标负责人/辅助人员时仅接收本项目通知，不全局广播 bid-Team 角色）</li>
     *   <li>项目负责人：{@code project.managerId} 直接加入（启用状态校验，禁用用户不接收通知）。</li>
     *   <li>全局广播：投标组长(bid-TeamLeader) 始终全局广播，与投标管理员口径一致。</li>
     *   <li>升级模式（逾期>7天）：追加 /bidAdmin 全局广播（按业务口径不做项目过滤）。</li>
     * </ul>
     *
     * @param globalTeamLeaderIds 扫描顶部预查的 bid-TeamLeader 用户 ID 集合（循环外预查避免 N 次重复查询）
     * @param globalBidAdminIds   扫描顶部预查的 /bidAdmin 用户 ID 集合（仅 overdueMode 时非空）
     */
    public List<Long> resolve(final Task task, final Project project, final boolean escalate,
                              final Map<Long, User> userCache,
                              final Set<Long> globalTeamLeaderIds,
                              final Set<Long> globalBidAdminIds) {
        Set<Long> ids = new LinkedHashSet<>();

        // 1. 项目级接收人：主投标负责人 + 副投标负责人 + 任务执行人（Spec 030 二次过滤）
        Set<ProjectNotificationRole> projectRoles = Set.of(
                ProjectNotificationRole.BID_LEAD,
                ProjectNotificationRole.BID_ASSISTANT,
                ProjectNotificationRole.TASK_EXECUTOR);
        List<Long> projectScoped = recipientResolver.resolveAndFilterProjectRecipients(
                task.getProjectId(), projectRoles, null, task.getAssigneeId());
        ids.addAll(projectScoped);

        // 2. 项目负责人（启用状态校验：禁用用户不接收通知）
        if (project != null && project.getManagerId() != null) {
            User manager = userCache.get(project.getManagerId());
            if (manager != null && Boolean.TRUE.equals(manager.getEnabled())) {
                ids.add(project.getManagerId());
            }
        }

        // 3. 全局广播：投标组长 bid-TeamLeader（始终接收所有项目预警，与投标管理员口径一致）
        ids.addAll(globalTeamLeaderIds);

        // 4. 升级模式：投标管理员 /bidAdmin 全局广播（按业务口径不过滤）
        if (escalate) {
            ids.addAll(globalBidAdminIds);
        }
        return new ArrayList<>(ids);
    }

    /**
     * 按角色码查询启用用户 ID 集合（复用 NotificationRecipientResolver，避免样板代码重复）。
     *
     * <p>委托给 {@link NotificationRecipientResolver#getUserIdsByRoleCodes}，本方法仅做
     * List→Set 转换以保持顺序稳定性（LinkedHashSet 保序，便于通知接收人列表可预测）。</p>
     */
    public Set<Long> loadEnabledUserIdsByRoleCode(final String roleCode) {
        if (roleCode == null) {
            return Set.of();
        }
        List<Long> userIds = recipientResolver.getUserIdsByRoleCodes(Set.of(roleCode));
        return userIds.stream()
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /**
     * 批量预查全局广播角色用户（扫描顶部调用一次，循环内复用）。
     *
     * @param overdueMode 是否逾期扫描模式（true 时额外预查 /bidAdmin）
     * @return 全局广播角色用户 ID 集合
     */
    public GlobalBroadcastIds preloadGlobalBroadcastIds(final boolean overdueMode) {
        Set<Long> teamLeaderIds = loadEnabledUserIdsByRoleCode(RoleProfileCatalog.BID_LEAD_CODE);
        Set<Long> bidAdminIds = overdueMode
                ? loadEnabledUserIdsByRoleCode(RoleProfileCatalog.BID_ADMIN_CODE)
                : Set.of();
        return new GlobalBroadcastIds(teamLeaderIds, bidAdminIds);
    }

    /** 全局广播角色用户 ID 集合（扫描顶部预查，循环内复用）。 */
    public record GlobalBroadcastIds(Set<Long> teamLeaderIds, Set<Long> bidAdminIds) {}
}
