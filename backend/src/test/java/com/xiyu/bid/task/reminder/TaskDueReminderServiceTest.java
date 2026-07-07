// Input: TaskDueReminderService + mocked dependencies
// Output: CO-533 即将到期/逾期扫描编排服务单测
// Pos: test/java/.../task/reminder - 应用服务单测
package com.xiyu.bid.task.reminder;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.RoleProfileCatalog;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.core.TaskDueReminderPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CO-533 任务到期/逾期提醒编排服务测试。
 *
 * <p>覆盖：
 * <ul>
 *   <li>即将到期扫描：命中有效任务 → 发送 DEADLINE 通知 + 更新 lastRemindedAt</li>
 *   <li>即将到期扫描：24h 内重复 → 跳过</li>
 *   <li>即将到期扫描：已完成任务 → 跳过（Repository 查询已排除，但策略二次防御）</li>
 *   <li>即将到期扫描：无接收人 → 中止</li>
 *   <li>即将到期扫描：非法 alertDays → 返回 empty</li>
 *   <li>逾期扫描：命中有效逾期任务 → 发送通知 + 更新 lastOverdueRemindedAt</li>
 *   <li>逾期扫描：逾期>7天 → 追加 /bidAdmin 接收人</li>
 *   <li>逾期扫描：24h 内重复 → 跳过</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class TaskDueReminderServiceTest {

    private static final Long SYSTEM_ACTOR_ID = 1L;

    @Mock private TaskRepository taskRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationApplicationService notificationApplicationService;
    @Mock private SystemActorResolver systemActorResolver;

    private TaskDueReminderService service;

    @BeforeEach
    void setUp() {
        systemActorResolver = mock(SystemActorResolver.class);
        lenient().when(systemActorResolver.resolveCached()).thenReturn(SYSTEM_ACTOR_ID);
        service = new TaskDueReminderService(
                taskRepository,
                projectRepository,
                userRepository,
                notificationApplicationService,
                systemActorResolver,
                new TaskDueReminderPolicy()
        );
    }

    @Test
    @DisplayName("runDueSoonScan - 命中有效任务：发送 DEADLINE 通知 + 更新 lastRemindedAt")
    void runDueSoonScan_HitValidTask_ShouldSendDeadlineAndUpdateLastRemindedAt() {
        Task task = sampleTask(LocalDateTime.now().plusDays(2), Task.Status.TODO, null);
        when(taskRepository.findByDueDateBetweenAndStatusNot(any(), any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findById(any())).thenReturn(Optional.of(sampleProject()));
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenReturn(List.of(user(101L), user(102L)));
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
        assertThat(req.recipientUserIds()).containsExactlyInAnyOrder(101L, 102L, task.getAssigneeId(), 200L);

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
        task.setAssigneeId(null);
        Project projectNoManager = new Project();
        projectNoManager.setId(20L);
        projectNoManager.setName("无项目经理项目");
        projectNoManager.setManagerId(null);
        when(taskRepository.findByDueDateBetweenAndStatusNot(any(), any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findById(any())).thenReturn(Optional.of(projectNoManager));
        when(userRepository.findEnabledByRoleProfileCodes(any())).thenReturn(List.of());

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
        when(projectRepository.findById(any())).thenReturn(Optional.of(sampleProject()));
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenReturn(List.of(user(101L), user(102L)));
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

        verify(taskRepository).save(task);
        assertThat(task.getLastOverdueRemindedAt()).isNotNull();
    }

    @Test
    @DisplayName("runOverdueScan - 逾期超过 7 天：追加 /bidAdmin 接收人")
    void runOverdueScan_Overdue8Days_ShouldEscalateRecipients() {
        Task task = sampleTask(LocalDateTime.now().minusDays(8), Task.Status.TODO, null, null);
        when(taskRepository.findByDueDateBeforeAndStatusNot(any(), eq(Task.Status.COMPLETED)))
                .thenReturn(List.of(task));
        when(projectRepository.findById(any())).thenReturn(Optional.of(sampleProject()));
        when(userRepository.findEnabledByRoleProfileCodes(any()))
                .thenReturn(List.of(user(101L), user(102L), user(103L)));
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(mock(com.xiyu.bid.notification.core.DispatchResult.class));

        TaskDueReminderService.ScanOutcome outcome = service.runOverdueScan(null);

        assertThat(outcome.notified()).isEqualTo(1);
        ArgumentCaptor<CreateNotificationRequest> reqCaptor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(reqCaptor.capture(), eq(SYSTEM_ACTOR_ID));
        CreateNotificationRequest req = reqCaptor.getValue();
        assertThat(req.recipientUserIds())
                .containsExactlyInAnyOrder(101L, 102L, 103L, task.getAssigneeId(), 200L);
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

    private User user(Long id) {
        User u = new User();
        u.setId(id);
        return u;
    }
}
