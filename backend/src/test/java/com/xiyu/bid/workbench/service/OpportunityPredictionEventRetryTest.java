// Input: mock repositories + 短事务协作者 + event publisher
// Output: 商机预测事件领取、失败释放、补发行为验证
// Pos: backend/src/test/java/com/xiyu/bid/workbench/service/ - 编排服务单测
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.integration.tenderevent.domain.OpportunityPredictionEventPayload;
import com.xiyu.bid.integration.tenderevent.infrastructure.sdk.OpportunityPredictionEventPublisher;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.project.entity.ProjectResult;
import com.xiyu.bid.project.repository.ProjectInitiationDetailsRepository;
import com.xiyu.bid.project.repository.ProjectResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.repository.UserRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("商机预测事件失败重试")
class OpportunityPredictionEventRetryTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock private ProjectRepository projectRepository;
    @Mock private ProjectResultRepository projectResultRepository;
    @Mock private ProjectInitiationDetailsRepository projectInitiationDetailsRepository;
    @Mock private TenderRepository tenderRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationRecipientResolver recipientResolver;
    @Mock private OpportunityPredictionReminderTransactionService transactionService;
    @Mock private OpportunityPredictionEventPublisher eventPublisher;
    @Mock private SystemActorResolver systemActorResolver;

    private OpportunityPredictionReminderService service;

    @BeforeEach
    void setUp() {
        service = new OpportunityPredictionReminderService(
                projectRepository, projectResultRepository, projectInitiationDetailsRepository,
                tenderRepository, userRepository, recipientResolver, transactionService,
                eventPublisher, systemActorResolver);
        Project project = Project.builder()
                .id(983L)
                .name("商机事件重试项目")
                .tenderId(9830L)
                .customer("测试客户")
                .customerType("LOCAL_SOE")
                .status(Project.Status.WON)
                .build();
        ProjectResult result = ProjectResult.builder()
                .projectId(983L)
                .resultType("WON")
                .servicePeriodEndDate(LocalDate.of(2026, 9, 30))
                .build();

        when(projectRepository.findAll()).thenReturn(List.of(project));
        when(projectResultRepository.findByProjectIdIn(List.of(983L))).thenReturn(List.of(result));
        when(projectInitiationDetailsRepository.findByProjectIdIn(List.of(983L))).thenReturn(List.of());
        when(tenderRepository.findAllById(Set.of(9830L))).thenReturn(List.of());
        when(recipientResolver.getUserIdsByRoleCodes(any())).thenReturn(List.of(10L));
        when(systemActorResolver.resolveCached()).thenReturn(999L);
    }

    @Test
    @DisplayName("已有待重试日志：领取成功后只重推事件并提交受理状态")
    void shouldRetryOnlyEventAndMarkPublished() {
        when(transactionService.findState(983L)).thenReturn(Optional.of(pendingState()));
        when(transactionService.claimPendingEvent(eq(983L), any(), any(), any())).thenReturn(true);
        when(eventPublisher.publish(any())).thenReturn(true);
        when(transactionService.completeEventPublish(eq(983L), any(), any())).thenReturn(true);
        when(tenderRepository.findAllById(Set.of(9830L))).thenReturn(List.of(Tender.builder()
                .id(9830L).projectType("工业品")
                .bidOpeningTime(LocalDateTime.of(2025, 8, 15, 9, 0)).build()));

        var outcome = service.runScan(TODAY);

        assertEquals(0, outcome.notified());
        assertEquals(1, outcome.eventRetried());
        verify(transactionService, never()).createNotificationAndClaim(any(), any(), any(), any(), any());
        ArgumentCaptor<OpportunityPredictionEventPayload> payloadCaptor =
                ArgumentCaptor.forClass(OpportunityPredictionEventPayload.class);
        verify(eventPublisher).publish(payloadCaptor.capture());
        assertEquals("INDUSTRIAL", payloadCaptor.getValue().projectType());
    }

    @Test
    @DisplayName("首次事件发布失败：提醒事务已完成，领取被释放供下次重试")
    void shouldReleaseInitialClaimWhenPublishFails() {
        when(transactionService.findState(983L)).thenReturn(Optional.empty());
        when(eventPublisher.publish(any())).thenReturn(false);

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.notified());
        verify(transactionService).createNotificationAndClaim(any(), any(), eq(999L), any(), any());
        verify(transactionService).releaseEventClaim(eq(983L), any());
    }

    @Test
    @DisplayName("事件连续失败：每次成功领取后只重推事件且不重复通知")
    void shouldKeepRetryingEventWithoutDuplicatingNotification() {
        when(transactionService.findState(983L)).thenReturn(Optional.of(pendingState()));
        when(transactionService.claimPendingEvent(eq(983L), any(), any(), any())).thenReturn(true);
        when(eventPublisher.publish(any())).thenReturn(false);

        service.runScan(TODAY);
        service.runScan(TODAY.plusDays(1));

        verify(eventPublisher, times(2)).publish(any());
        verify(transactionService, times(2)).releaseEventClaim(eq(983L), any());
        verify(transactionService, never()).createNotificationAndClaim(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("系统通知账号不可用时，已有待重试事件仍继续补发")
    void shouldRetryExistingEventWithoutSystemActor() {
        when(systemActorResolver.resolveCached()).thenReturn(null);
        when(transactionService.findState(983L)).thenReturn(Optional.of(pendingState()));
        when(transactionService.claimPendingEvent(eq(983L), any(), any(), any())).thenReturn(true);
        when(eventPublisher.publish(any())).thenReturn(true);
        when(transactionService.completeEventPublish(eq(983L), any(), any())).thenReturn(true);

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.eventRetried());
        verify(eventPublisher).publish(any());
    }

    @Test
    @DisplayName("其他实例已领取：当前实例不重复调用 SDK")
    void shouldSkipWhenAnotherInstanceOwnsClaim() {
        when(transactionService.findState(983L)).thenReturn(Optional.of(pendingState()));
        when(transactionService.claimPendingEvent(eq(983L), any(), any(), any())).thenReturn(false);

        var outcome = service.runScan(TODAY);

        assertEquals(1, outcome.skipped());
        verify(eventPublisher, never()).publish(any());
    }

    private OpportunityPredictionReminderTransactionService.ReminderState pendingState() {
        return new OpportunityPredictionReminderTransactionService.ReminderState(
                LocalDate.of(2026, 6, 30), null);
    }
}
