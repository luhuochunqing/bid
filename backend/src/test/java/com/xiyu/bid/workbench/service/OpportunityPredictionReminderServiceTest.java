// Input: mock repository + notification service + event publisher
// Output: 编排服务扫描/去重/接收人/事件发布行为验证
// Pos: backend/src/test/java/com/xiyu/bid/workbench/service/ - 编排服务单测
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.infrastructure.sdk.OpportunityPredictionEventPublisher;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import com.xiyu.bid.workbench.repository.OpportunityPredictionReminderLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 商机预测提醒编排服务测试：Mock 掉 repository / 通知 / 事件发布，
 * 验证"扫描 → 判定 → 通知 → 落日志 → 发事件"主链路与去重分支。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OpportunityPredictionReminderService 测试")
class OpportunityPredictionReminderServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 24);
    private static final Long SYSTEM_ACTOR = 999L;
    private static final List<Long> ROLE_RECIPIENTS = List.of(10L, 11L);

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectResultRepository projectResultRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationRecipientResolver recipientResolver;
    @Mock private NotificationApplicationService notificationApplicationService;
    @Mock private OpportunityPredictionReminderLogRepository reminderLogRepository;
    @Mock private OpportunityPredictionEventPublisher eventPublisher;
    @Mock private SystemActorResolver systemActorResolver;

    private OpportunityPredictionReminderService service;

    @BeforeEach
    void setUp() {
        service = new OpportunityPredictionReminderService(
                projectRepository, projectResultRepository, tenderRepository, userRepository,
                recipientResolver, notificationApplicationService, reminderLogRepository,
                eventPublisher, systemActorResolver);
        lenient().when(systemActorResolver.resolveCached()).thenReturn(SYSTEM_ACTOR);
        lenient().when(recipientResolver.getUserIdsByRoleCodes(any())).thenReturn(ROLE_RECIPIENTS);
        lenient().when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.valid());
        lenient().when(eventPublisher.publish(any())).thenReturn(true);
    }

    /** 构造"已到预测时点"的项目：地方国企 + 截止时间已过（预测 = 截止-3月 < 今天）。 */
    private Project reachedProject(Long id, Long tenderId, Long managerId) {
        return Project.builder()
                .id(id)
                .name("测试项目" + id)
                .tenderId(tenderId)
                .customerType("LOCAL_SOE")
                .customer("客户" + id)
                .managerId(managerId)
                .status(Project.Status.WON)
                .build();
    }

    @Test
    @DisplayName("命中提醒窗口：通知 + 落日志 + 发事件，接收人含角色与项目经理")
    void shouldNotifyWhenPredictedReached() {
        Project project = reachedProject(1L, 100L, 50L);
        ProjectResult result = ProjectResult.builder()
                .projectId(1L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1)) // 预测 = 2026-03-01 < 今天
                .servicePeriodYears(new BigDecimal("3"))
                .build();
        Tender tender = Tender.builder()
                .id(100L)
                .purchaserName("招标主体X")
                .bidOpeningTime(LocalDateTime.of(2025, 8, 15, 9, 0))
                .build();
        User manager = User.builder().id(50L).enabled(true).build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(1L))).thenReturn(List.of(result));
        when(tenderRepository.findAllById(Set.of(100L))).thenReturn(List.of(tender));
        when(userRepository.findByIdIn(Set.of(50L))).thenReturn(List.of(manager));
        when(reminderLogRepository.findByProjectId(1L)).thenReturn(Optional.empty());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.notified());
        assertEquals(0, outcome.skipped());

        ArgumentCaptor<CreateNotificationRequest> reqCaptor = ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(reqCaptor.capture(), eq(SYSTEM_ACTOR));
        CreateNotificationRequest req = reqCaptor.getValue();
        assertEquals(NotificationType.OPPORTUNITY_PREDICTION.name(), req.type());
        assertEquals("Project", req.sourceEntityType());
        assertEquals(1L, req.sourceEntityId());
        assertEquals("【商机预测】招标主体X", req.title());
        assertTrue(req.body().contains("请到CRM创建商机"));
        assertEquals(Set.of(10L, 11L, 50L), Set.copyOf(req.recipientUserIds()));
        assertEquals("/bidding/customer-opportunities", req.payload().get("targetUrl"));

        ArgumentCaptor<OpportunityPredictionReminderLog> logCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionReminderLog.class);
        verify(reminderLogRepository).save(logCaptor.capture());
        assertEquals(1L, logCaptor.getValue().getProjectId());
        assertEquals(LocalDate.of(2026, 3, 1), logCaptor.getValue().getPredictedBidDate());

        ArgumentCaptor<OpportunityPredictionEventPayload> evtCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionEventPayload.class);
        verify(eventPublisher).publish(evtCaptor.capture());
        assertEquals(1L, evtCaptor.getValue().projectId());
        assertEquals("招标主体X", evtCaptor.getValue().purchaserName());
        assertEquals(LocalDate.of(2026, 3, 1), evtCaptor.getValue().predictedBidDate());
    }

    @Test
    @DisplayName("已提醒过：不重复通知、不落日志、不发事件")
    void shouldSkipWhenAlreadyNotified() {
        Project project = reachedProject(2L, 200L, 60L);
        ProjectResult result = ProjectResult.builder()
                .projectId(2L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(2L))).thenReturn(List.of(result));
        when(tenderRepository.findAllById(Set.of(200L))).thenReturn(List.of());
        when(userRepository.findByIdIn(Set.of(60L))).thenReturn(List.of(User.builder().id(60L).enabled(true).build()));
        when(reminderLogRepository.findByProjectId(2L)).thenReturn(Optional.of(
                OpportunityPredictionReminderLog.builder()
                        .projectId(2L)
                        .predictedBidDate(LocalDate.of(2026, 3, 1))
                        .notifiedAt(LocalDateTime.of(2026, 8, 20, 9, 0))
                        .build()));

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        assertEquals(0, outcome.notified());
        verify(notificationApplicationService, never()).createNotification(any(), any());
        verify(reminderLogRepository, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }

    @Test
    @DisplayName("预测时间未到：不通知")
    void shouldSkipWhenPredictedInFuture() {
        Project project = reachedProject(3L, 300L, 70L);
        ProjectResult result = ProjectResult.builder()
                .projectId(3L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2027, 1, 1)) // 预测 = 2026-10-01 > 今天
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(3L))).thenReturn(List.of(result));
        when(tenderRepository.findAllById(Set.of(300L))).thenReturn(List.of());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        assertEquals(0, outcome.notified());
        verify(notificationApplicationService, never()).createNotification(any(), any());
        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("无接收人（角色空且项目经理未启用）：跳过")
    void shouldSkipWhenNoRecipients() {
        Project project = reachedProject(4L, 400L, 80L);
        ProjectResult result = ProjectResult.builder()
                .projectId(4L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 6, 1))
                .build();
        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(4L))).thenReturn(List.of(result));
        when(recipientResolver.getUserIdsByRoleCodes(any())).thenReturn(List.of());
        when(userRepository.findByIdIn(Set.of(80L))).thenReturn(List.of(User.builder().id(80L).enabled(false).build()));
        when(reminderLogRepository.findByProjectId(4L)).thenReturn(Optional.empty());

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        assertEquals(0, outcome.notified());
        verify(notificationApplicationService, never()).createNotification(any(), any());
        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    @DisplayName("无项目：扫描空结果")
    void shouldReturnEmptyWhenNoProjects() {
        when(projectRepository.findAll()).thenReturn(List.of());
        var outcome = service.runScan(TODAY);
        assertEquals(0, outcome.scanned());
        assertEquals(0, outcome.notified());
    }
}
