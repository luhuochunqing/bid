// Input: TaskRepository + ProjectRepository + UserRepository + NotificationApplicationService + SystemActorResolver + TaskDueReminderPolicy
// Output: CO-533 任务到期/逾期提醒扫描结果（scanned/notified/skipped）
// Pos: task/reminder - 应用服务编排层，依赖 shell 但不持有业务规则
// 维护声明:
//   - 业务规则委托给 TaskDueReminderPolicy（纯核心）；
//   - 接收人解析委托给 TaskReminderRecipientResolver（避免本类超 300 行预算）；
//   - 通知类型复用 NotificationType.DEADLINE，不新增枚举；
//   - 24h 去重通过 Task.lastRemindedAt/lastOverdueRemindedAt 字段控制；
//   - 消息模板渲染下沉到 TaskReminderMessageBuilder（避免本类超 300 行预算）；
//   - 批量预查 Project/User 构造 Map 缓存，避免循环内 N+1 查询（R3）；
//   - runDueSoonScan/runOverdueScan 标注 @Transactional，保证扫描原子性（R4）。
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.core.TaskDueReminderPolicy;
import com.xiyu.bid.task.core.TaskDueReminderPolicy.SkipReason;
import com.xiyu.bid.task.core.TaskDueReminderPolicy.TaskReminderState;
import com.xiyu.bid.task.reminder.TaskReminderRecipientResolver.GlobalBroadcastIds;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * CO-533 投标项目任务到期/逾期提醒编排服务。
 *
 * <p>业务规则委托给 {@link TaskDueReminderPolicy}（纯核心），本类只做编排：
 * 查数据 → 调策略 → 解析接收人 → 发通知 → 更新去重字段。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskDueReminderService {

    /** 默认提前提醒天数（蓝图 §1.1）。 */
    public static final int DEFAULT_ALERT_DAYS = 3;

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final NotificationApplicationService notificationApplicationService;
    private final SystemActorResolver systemActorResolver;
    private final TaskDueReminderPolicy policy;
    private final TaskReminderRecipientResolver recipientResolver;

    /**
     * 运行即将到期扫描。
     *
     * @param alertDays     提前提醒天数（&lt;=0 视为非法，直接返回 empty）
     * @param detailUrlBase 详情链接前缀（可为 null，使用默认）
     * @return 扫描结果
     */
    @Transactional
    public ScanOutcome runDueSoonScan(final int alertDays, final String detailUrlBase) {
        if (alertDays <= 0) {
            log.warn("[CO-533] alertDays={} 非法，跳过即将到期扫描", alertDays);
            return ScanOutcome.empty();
        }
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowStart = today.atStartOfDay();
        LocalDateTime windowEnd = today.plusDays(alertDays).atTime(23, 59, 59);
        List<Task> tasks = taskRepository.findByDueDateBetweenAndStatusNot(
                windowStart, windowEnd, Task.Status.COMPLETED);
        log.info("[CO-533] 即将到期扫描: alertDays={} 命中 {} 个任务", alertDays, tasks.size());
        return executeScan(tasks, today, now, alertDays, detailUrlBase, false);
    }

    /**
     * 运行逾期扫描。
     *
     * @param detailUrlBase 详情链接前缀（可为 null，使用默认）
     * @return 扫描结果
     */
    @Transactional
    public ScanOutcome runOverdueScan(final String detailUrlBase) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime cutoff = today.atStartOfDay();
        List<Task> tasks = taskRepository.findByDueDateBeforeAndStatusNot(
                cutoff, Task.Status.COMPLETED);
        log.info("[CO-533] 逾期扫描: 命中 {} 个任务", tasks.size());
        return executeScan(tasks, today, now, 0, detailUrlBase, true);
    }

    private ScanOutcome executeScan(final List<Task> tasks, final LocalDate today,
                                    final LocalDateTime now, final int alertDays,
                                    final String detailUrlBase, final boolean overdueMode) {
        if (tasks.isEmpty()) {
            return ScanOutcome.empty();
        }
        Long systemActor = systemActorResolver.resolveCached();
        if (systemActor == null) {
            log.warn("[CO-533] system actor 未解析，中止扫描避免 created_by=null");
            return new ScanOutcome(tasks.size(), 0, tasks.size());
        }

        // 批量预查 Project（避免循环内 N+1 查询）
        Map<Long, Project> projectCache = batchLoadProjects(tasks);
        // 收集 assignee + manager 用户 ID 并批量查询
        Set<Long> userIdsToLoad = new java.util.LinkedHashSet<>();
        for (Task t : tasks) {
            if (t.getAssigneeId() != null) {
                userIdsToLoad.add(t.getAssigneeId());
            }
            Project p = projectCache.get(t.getProjectId());
            if (p != null && p.getManagerId() != null) {
                userIdsToLoad.add(p.getManagerId());
            }
        }
        Map<Long, User> userCache = batchLoadUsers(userIdsToLoad);

        // 批量预查全局广播角色用户（避免循环内 N 次重复查询同一角色用户列表）
        GlobalBroadcastIds globalIds = recipientResolver.preloadGlobalBroadcastIds(overdueMode);

        int notified = 0;
        int skipped = 0;
        for (Task task : tasks) {
            TaskReminderState state = toState(task);
            SkipReason reason = overdueMode
                    ? policy.shouldSkipOverdue(state, today, now)
                    : policy.shouldSkipDueSoon(state, today, alertDays, now);
            if (reason != null) {
                log.debug("[CO-533] 跳过任务 id={} reason={}", task.getId(), reason);
                skipped++;
                continue;
            }
            boolean escalate = overdueMode && policy.shouldEscalate(task.getDueDate(), today);
            Project project = projectCache.get(task.getProjectId());
            List<Long> recipients = recipientResolver.resolve(
                    task, project, escalate, userCache,
                    globalIds.teamLeaderIds(), globalIds.bidAdminIds());
            if (recipients.isEmpty()) {
                log.warn("[CO-533] 任务 id={} 无接收人，跳过", task.getId());
                skipped++;
                continue;
            }
            User assigneeUser = task.getAssigneeId() != null
                    ? userCache.get(task.getAssigneeId()) : null;
            User managerUser = project != null && project.getManagerId() != null
                    ? userCache.get(project.getManagerId()) : null;
            boolean sent = sendNotification(task, recipients, overdueMode, detailUrlBase,
                    today, systemActor, project, assigneeUser, managerUser);
            if (sent) {
                if (overdueMode) {
                    task.setLastOverdueRemindedAt(now);
                } else {
                    task.setLastRemindedAt(now);
                }
                taskRepository.save(task);
                notified++;
            } else {
                skipped++;
            }
        }
        log.info("[CO-533] 扫描完成: scanned={} notified={} skipped={} overdue={}",
                tasks.size(), notified, skipped, overdueMode);
        return new ScanOutcome(tasks.size(), notified, skipped);
    }

    private boolean sendNotification(final Task task, final List<Long> recipients,
                                     final boolean overdueMode, final String detailUrlBase,
                                     final LocalDate today, final Long systemActor,
                                     final Project project, final User assigneeUser,
                                     final User managerUser) {
        long days = overdueMode
                ? -policy.computeRemainingDays(task.getDueDate(), today)
                : policy.computeRemainingDays(task.getDueDate(), today);
        String projectName = project != null ? project.getName() : "";
        String title = TaskReminderMessageBuilder.buildTitle(task.getTitle(), days, overdueMode);
        String body = TaskReminderMessageBuilder.buildBody(task, projectName, days, overdueMode,
                detailUrlBase, assigneeUser, managerUser);
        try {
            notificationApplicationService.createNotification(
                    new CreateNotificationRequest(
                            NotificationType.DEADLINE.name(),
                            "Task",
                            task.getId(),
                            title,
                            body,
                            null,
                            recipients
                    ),
                    systemActor
            );
            return true;
        } catch (RuntimeException ex) {
            log.error("[CO-533] 任务提醒派发失败 taskId={}: {}", task.getId(), ex.getMessage(), ex);
            return false;
        }
    }

    private TaskReminderState toState(final Task task) {
        return new TaskReminderState(
                task.getDueDate(),
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getLastRemindedAt(),
                task.getLastOverdueRemindedAt()
        );
    }

    /** 批量加载 Project，避免循环内 N+1 查询。 */
    private Map<Long, Project> batchLoadProjects(final List<Task> tasks) {
        Set<Long> projectIds = tasks.stream()
                .map(Task::getProjectId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        if (projectIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, Function.identity(), (a, b) -> a));
    }

    /** 批量加载 User，避免循环内 N+1 查询。 */
    private Map<Long, User> batchLoadUsers(final Set<Long> userIds) {
        if (userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity(), (a, b) -> a));
    }

    /** 扫描结果。 */
    public record ScanOutcome(int scanned, int notified, int skipped) {
        public static ScanOutcome empty() {
            return new ScanOutcome(0, 0, 0);
        }
    }
}
