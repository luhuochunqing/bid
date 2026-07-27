// Input: TaskDueReminderService + mocked dependencies
// Output: CO-533 即将到期/逾期扫描编排服务单测
// Pos: test/java/.../task/reminder - 应用服务单测
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.core.TaskDueReminderPolicy;
import com.xiyu.bid.task.reminder.TaskReminderRecipientResolver.GlobalBroadcastIds;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-533 任务到期/逾期提醒编排服务测试。
 *
 * <p>覆盖编排逻辑：扫描 → 去重 → 调用解析器 → 发通知 → 更新字段。
 * 接收人解析细节由 {@link TaskReminderRecipientResolverTest} 覆盖。</p>
 */
@ExtendWith(MockitoExtension.class)
class TaskDueReminderServiceTest {

    private static final Long SYSTEM_ACTOR_ID = 1L;
    private static final GlobalBroadcastIds DUE_SOON_IDS = new GlobalBroadcastIds(Set.of(103L), Set.of());
    private static final GlobalBroadcastIds OVERDUE_IDS = new GlobalBroadcastIds(Set.of(103L), Set.of(104L));

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationApplicationService notificationApplicationService;
    @Mock private TaskReminderRecipientResolver recipientResolver;

    private TaskDueReminderService service;

    @BeforeEach
    void setUp() {
        SystemActorResolver systemActorResolver = mock(SystemActorResolver.class);
        lenient().when(systemActorResolver.resolveCached()).thenReturn(SYSTEM_ACTOR_ID);
        service = new TaskDueReminderService(
                taskRepository,
                projectRepository,
                userRepository,
                notificationApplicationService,
                systemActorResolver,
                new TaskDueReminderPolicy(),
                recipientResolver
        );
    }

    @Test
    @DisplayName("runDueSoonScan - 命中有效任务：发送 DEADLINE 通知 + 更新 lastRemindedAt")
    void runDueSoonScan_HitValidTask_ShouldSendDeadlineAndUpdateLastRemindedAt() {
        Task task = sampleTask(LocalDateTime.now().plusDays(2), Task.Status.TODO, null);
        when(taskRepository.findByDueDateBetweenAndStatusNot(any(), any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(sampleProject()));
        when(userRepository.findByIdIn(anyCollection()))
                .thenReturn(List.of(userWithFullName(50L, "张三"), userWithFullName(200L, "李四")));
        when(recipientResolver.preloadGlobalBroadcastIds(eq(false))).thenReturn(DUE_SOON_IDS);
        when(recipientResolver.resolve(any(), any(), anyBoolean(), any(), eq(Set.of(103L)), eq(Set.of())))
                .thenReturn(List.of(101L, 102L, 103L, task.getAssigneeId(), 200L));
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(mock(com.xiyu.bid.notification.core.DispatchResult.class));

        TaskDueReminderService.ScanOutcome outcome = service.runDueSoonScan(3, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(1);
        assertThat(outcome.skipped()).isEqualTo(0);

        ArgumentCaptor<CreateNotificationRequest> reqCaptor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(reqCaptor.capture(), eq(SYSTEM_ACTOR_ID));
        CreateNotificationRequest req = reqCaptor.getValue();
        assertThat(req.type()).isEqualTo("DEADLINE");
        assertThat(req.sourceEntityType()).isEqualTo("Task");
        assertThat(req.sourceEntityId()).isEqualTo(task.getId());
        assertThat(req.title()).contains("【任务到期提醒】").contains("2 天到期");
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(101L, 102L, 103L, task.getAssigneeId(), 200L);
        assertThat(req.body()).contains("任务执行人：张三（50）");
        assertThat(req.body()).contains("任务审核人：李四（项目负责人）");

        verify(taskRepository).save(task);
        assertThat(task.getLastRemindedAt()).isNotNull();
    }

    @Test
    @DisplayName("runDueSoonScan - 24h 内重复扫描：被去重")
    void runDueSoonScan_DuplicateWithin24h_ShouldSkipByDedup() {
        Task task = sampleTask(LocalDateTime.now().plusDays(2), Task.Status.TODO,
                LocalDateTime.now().minusHours(3));
        when(taskRepository.findByDueDateBetweenAndStatusNot(any(), any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));

        TaskDueReminderService.ScanOutcome outcome = service.runDueSoonScan(3, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(0);
        assertThat(outcome.skipped()).isEqualTo(1);
        verify(notificationApplicationService, never()).createNotification(any(), any());
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("runDueSoonScan - 已完成任务：被策略跳过（COMPLETED）")
    void runDueSoonScan_CompletedTask_ShouldSkip() {
        Task task = sampleTask(LocalDateTime.now().plusDays(2), Task.Status.COMPLETED, null);
        when(taskRepository.findByDueDateBetweenAndStatusNot(any(), any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));

        TaskDueReminderService.ScanOutcome outcome = service.runDueSoonScan(3, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(0);
        assertThat(outcome.skipped()).isEqualTo(1);
        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("runDueSoonScan - 无接收人：中止派发")
    void runDueSoonScan_NoRecipients_ShouldAbort() {
        Task task = sampleTask(LocalDateTime.now().plusDays(2), Task.Status.TODO, null);
        when(taskRepository.findByDueDateBetweenAndStatusNot(any(), any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(sampleProject()));
        when(userRepository.findByIdIn(anyCollection())).thenReturn(List.of());
        when(recipientResolver.preloadGlobalBroadcastIds(eq(false)))
                .thenReturn(new GlobalBroadcastIds(Set.of(), Set.of()));
        when(recipientResolver.resolve(any(), any(), anyBoolean(), any(), eq(Set.of()), eq(Set.of())))
                .thenReturn(List.of());

        TaskDueReminderService.ScanOutcome outcome = service.runDueSoonScan(3, null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(0);
        assertThat(outcome.skipped()).isEqualTo(1);
        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("runDueSoonScan - 非法 alertDays=0：返回 empty")
    void runDueSoonScan_InvalidAlertDays_ShouldReturnEmpty() {
        TaskDueReminderService.ScanOutcome outcome = service.runDueSoonScan(0, null);

        assertThat(outcome.scanned()).isZero();
        assertThat(outcome.notified()).isZero();
        verify(taskRepository, never()).findByDueDateBetweenAndStatusNot(any(), any(), any());
    }

    @Test
    @DisplayName("runOverdueScan - 命中有效逾期任务：发送通知 + 更新 lastOverdueRemindedAt")
    void runOverdueScan_HitValidTask_ShouldSendNotificationAndUpdateLastOverdueRemindedAt() {
        Task task = sampleTask(LocalDateTime.now().minusDays(3), Task.Status.TODO, null, null);
        when(taskRepository.findByDueDateBeforeAndStatusNot(any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(sampleProject()));
        when(userRepository.findByIdIn(anyCollection()))
                .thenReturn(List.of(userWithFullName(50L, "张三"), userWithFullName(200L, "李四")));
        when(recipientResolver.preloadGlobalBroadcastIds(eq(true))).thenReturn(OVERDUE_IDS);
        when(recipientResolver.resolve(any(), any(), anyBoolean(), any(), eq(Set.of(103L)), eq(Set.of(104L))))
                .thenReturn(List.of(101L, 102L, 103L, 104L, task.getAssigneeId(), 200L));
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(mock(com.xiyu.bid.notification.core.DispatchResult.class));

        TaskDueReminderService.ScanOutcome outcome = service.runOverdueScan(null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(1);
        assertThat(outcome.skipped()).isEqualTo(0);

        ArgumentCaptor<CreateNotificationRequest> reqCaptor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(reqCaptor.capture(), eq(SYSTEM_ACTOR_ID));
        CreateNotificationRequest req = reqCaptor.getValue();
        assertThat(req.title()).contains("【任务逾期提醒】").contains("已逾期 3 天");
        assertThat(req.body()).contains("任务执行人：张三（50）");
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(101L, 102L, 103L, 104L, task.getAssigneeId(), 200L);

        verify(taskRepository).save(task);
        assertThat(task.getLastOverdueRemindedAt()).isNotNull();
    }

    @Test
    @DisplayName("runOverdueScan - 逾期超过 7 天：preloadGlobalBroadcastIds(true) 预查 /bidAdmin")
    void runOverdueScan_Overdue8Days_ShouldEscalateRecipients() {
        Task task = sampleTask(LocalDateTime.now().minusDays(8), Task.Status.TODO, null, null);
        when(taskRepository.findByDueDateBeforeAndStatusNot(any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findAllById(anyCollection())).thenReturn(List.of(sampleProject()));
        when(userRepository.findByIdIn(anyCollection()))
                .thenReturn(List.of(userWithFullName(50L, "张三"), userWithFullName(200L, "李四")));
        when(recipientResolver.preloadGlobalBroadcastIds(eq(true))).thenReturn(OVERDUE_IDS);
        when(recipientResolver.resolve(any(), any(), eq(true), any(), eq(Set.of(103L)), eq(Set.of(104L))))
                .thenReturn(List.of(101L, 102L, 103L, 104L, task.getAssigneeId(), 200L));
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(mock(com.xiyu.bid.notification.core.DispatchResult.class));

        service.runOverdueScan(null);

        // 验证逾期模式预查了 /bidAdmin（true 触发）
        verify(recipientResolver).preloadGlobalBroadcastIds(eq(true));
    }

    @Test
    @DisplayName("runOverdueScan - 24h 内重复扫描：被去重")
    void runOverdueScan_DuplicateWithin24h_ShouldSkipByDedup() {
        Task task = sampleTask(LocalDateTime.now().minusDays(3), Task.Status.TODO, null,
                LocalDateTime.now().minusHours(5));
        when(taskRepository.findByDueDateBeforeAndStatusNot(any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));

        TaskDueReminderService.ScanOutcome outcome = service.runOverdueScan(null);

        assertThat(outcome.scanned()).isEqualTo(1);
        assertThat(outcome.notified()).isEqualTo(0);
        assertThat(outcome.skipped()).isEqualTo(1);
        verify(notificationApplicationService, never()).createNotification(any(), any());
        verify(taskRepository, never()).save(any());
    }

    private Task sampleTask(LocalDateTime dueDate, Task.Status status, LocalDateTime lastRemindedAt) {
        return sampleTask(dueDate, status, lastRemindedAt, null);
    }

    private Task sampleTask(LocalDateTime dueDate, Task.Status status,
                           LocalDateTime lastRemindedAt, LocalDateTime lastOverdueRemindedAt) {
        Task task = new Task();
        task.setId(10L);
        task.setTitle("编写技术方案");
        task.setStatus(status);
        task.setDueDate(dueDate);
        task.setAssigneeId(50L);
        task.setProjectId(20L);
        task.setLastRemindedAt(lastRemindedAt);
        task.setLastOverdueRemindedAt(lastOverdueRemindedAt);
        return task;
    }

    private Project sampleProject() {
        Project project = new Project();
        project.setId(20L);
        project.setName("XXX 政府采购项目");
        project.setManagerId(200L);
        return project;
    }

    private User userWithFullName(Long id, String fullName) {
        User u = new User();
        u.setId(id);
        u.setFullName(fullName);
        return u;
    }
}
