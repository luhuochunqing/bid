// Input: TaskRepository + ProjectRepository + UserRepository + NotificationApplicationService + SystemActorResolver + TaskDueReminderPolicy
// Output: CO-533 任务到期/逾期提醒扫描结果（scanned/notified/skipped）
// Pos: task/reminder - 应用服务编排层，依赖 shell 但不持有业务规则
// 维护声明:
//   - 业务规则委托给 TaskDueReminderPolicy（纯核心）；
//   - 接收人解析：任务执行人 + 项目负责人 + 投标负责人(bid-TeamLeader) + 投标辅助人员(bid-Team)，逾期>7天追加 /bidAdmin；
//   - 通知类型复用 NotificationType.DEADLINE，不新增枚举；
//   - 24h 去重通过 Task.lastRemindedAt/lastOverdueRemindedAt 字段控制。
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CO-533 投标项目任务到期/逾期提醒编排服务。
 *
 * <p>对齐蓝图 §1.1 即将到期提醒与 §1.2 逾期/超期提醒：
 * <ul>
 *   <li>即将到期扫描：dueDate 在 (today, today+alertDays] 区间且未完成的任务</li>
 *   <li>逾期扫描：dueDate &lt; today 且未完成的任务</li>
 *   <li>24h 去重：通过 Task.lastRemindedAt/lastOverdueRemindedAt 字段</li>
 *   <li>升级机制：逾期超过 7 天追加 /bidAdmin 接收人</li>
 * </ul>
 *
 * <p>业务规则委托给 {@link TaskDueReminderPolicy}（纯核心），本类只做编排：
 * 查数据 → 调策略 → 解析接收人 → 发通知 → 更新去重字段。
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

    /**
     * 运行即将到期扫描。
     *
     * @param alertDays     提前提醒天数（&lt;=0 视为非法，直接返回 empty）
     * @param detailUrlBase 详情链接前缀（可为 null，使用默认）
     * @return 扫描结果
     */
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
            List<Long> recipients = resolveRecipients(task, escalate);
            if (recipients.isEmpty()) {
                log.warn("[CO-533] 任务 id={} 无接收人，跳过", task.getId());
                skipped++;
                continue;
            }
            boolean sent = sendNotification(task, recipients, overdueMode, detailUrlBase, today, systemActor);
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

    /**
     * 解析接收人：任务执行人 + 项目负责人 + 投标负责人 + 投标辅助人员。
     * 升级模式（逾期>7天）追加 /bidAdmin。
     */
    private List<Long> resolveRecipients(final Task task, final boolean escalate) {
        Set<Long> ids = new LinkedHashSet<>();
        if (task.getAssigneeId() != null) {
            ids.add(task.getAssigneeId());
        }
        Project project = task.getProjectId() != null
                ? projectRepository.findById(task.getProjectId()).orElse(null) : null;
        if (project != null && project.getManagerId() != null) {
            ids.add(project.getManagerId());
        }
        List<String> roleCodes = new ArrayList<>();
        roleCodes.add(RoleProfileCatalog.BID_LEAD_CODE);
        roleCodes.add(RoleProfileCatalog.BID_SPECIALIST_CODE);
        if (escalate) {
            roleCodes.add(RoleProfileCatalog.BID_ADMIN_CODE);
        }
        ids.addAll(userRepository.findEnabledByRoleProfileCodes(roleCodes)
                .stream().map(User::getId).collect(Collectors.toList()));
        return new ArrayList<>(ids);
    }

    private boolean sendNotification(final Task task, final List<Long> recipients,
                                     final boolean overdueMode, final String detailUrlBase,
                                     final LocalDate today, final Long systemActor) {
        long days = overdueMode
                ? -policy.computeRemainingDays(task.getDueDate(), today)
                : policy.computeRemainingDays(task.getDueDate(), today);
        Project project = task.getProjectId() != null
                ? projectRepository.findById(task.getProjectId()).orElse(null) : null;
        String projectName = project != null ? project.getName() : "";
        String title = buildTitle(task.getTitle(), days, overdueMode);
        String body = buildBody(task, projectName, days, overdueMode, task.getProjectId(), task.getId(), detailUrlBase);
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

    private String buildTitle(final String taskTitle, final long days, final boolean overdueMode) {
        String safeTitle = taskTitle != null ? taskTitle : "";
        if (overdueMode) {
            return String.format("【任务逾期提醒】《%s》已逾期 %d 天", safeTitle, days);
        }
        return String.format("【任务到期提醒】《%s》还有 %d 天到期", safeTitle, days);
    }

    private String buildBody(final Task task, final String projectName, final long days,
                             final boolean overdueMode, final Long projectId, final Long taskId,
                             final String detailUrlBase) {
        String safeTitle = task.getTitle() != null ? task.getTitle() : "";
        String statusText = task.getStatus() != null ? task.getStatus().name() : "";
        String daysLabel = overdueMode ? "逾期天数" : "剩余天数";
        String daysValue = overdueMode ? String.valueOf(days) : days + " 天";
        String link = String.format("/project/%d/drafting?taskId=%d", projectId, taskId);
        if (detailUrlBase != null && !detailUrlBase.isBlank()) {
            link = detailUrlBase + link;
        }
        return String.format(
                "任务名称：%s\n所属项目：%s\n任务执行人：%s\n任务状态：%s\n截止日期：%s\n%s：%s\n跳转详情：%s",
                safeTitle,
                projectName,
                task.getAssigneeId() != null ? task.getAssigneeId() : "",
                statusText,
                task.getDueDate() != null ? task.getDueDate().toLocalDate() : "",
                daysLabel,
                daysValue,
                link
        );
    }

    private TaskReminderState toState(final Task task) {
        return new TaskReminderState(
                task.getDueDate(),
                task.getStatus() != null ? task.getStatus().name() : null,
                task.getLastRemindedAt(),
                task.getLastOverdueRemindedAt()
        );
    }

    /** 扫描结果。 */
    public record ScanOutcome(int scanned, int notified, int skipped) {
        public static ScanOutcome empty() {
            return new ScanOutcome(0, 0, 0);
        }
    }
}
