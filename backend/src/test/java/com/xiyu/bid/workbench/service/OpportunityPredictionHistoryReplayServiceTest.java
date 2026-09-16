// Input: 历史提醒日志、项目、实时收件人快照与幂等事务边界
// Output: 历史补发 preview/execute 的候选、收件人、通知与失败计数验证
// Pos: backend/src/test/java/com/xiyu/bid/workbench/service/ - 历史补发编排单测
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.alerts.service.SystemActorResolver;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import com.xiyu.bid.workbench.repository.OpportunityPredictionReminderLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("商机预测历史补发服务")
class OpportunityPredictionHistoryReplayServiceTest {

    private static final LocalDate RUN_DATE = LocalDate.of(2026, 9, 16);
    private static final LocalDate CUTOFF = LocalDate.of(2026, 9, 16);
    private static final Long SYSTEM_ACTOR = 999L;

    @Mock private OpportunityPredictionReminderLogRepository reminderLogRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private OpportunityPredictionRecipientResolver recipientResolver;
    @Mock private OpportunityPredictionReminderTransactionService transactionService;
    @Mock private SystemActorResolver systemActorResolver;

    private OpportunityPredictionHistoryReplayService service;

    @BeforeEach
    void setUp() {
        service = new OpportunityPredictionHistoryReplayService(
                reminderLogRepository, projectRepository, recipientResolver,
                transactionService, systemActorResolver);
    }

    @Test
    @DisplayName("preview 使用截止日和执行日零点查历史候选且绝不写入")
    void previewUsesHistoricalBoundaryAndNeverWrites() {
        OpportunityPredictionReminderLog log = reminder(1L, LocalDate.of(2026, 8, 1));
        when(reminderLogRepository
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
                        CUTOFF, RUN_DATE.atStartOfDay()))
                .thenReturn(List.of(log));
        when(projectRepository.findAllById(List.of(1L)))
                .thenReturn(List.of(project(1L, 30L)));
        when(transactionService.findExistingNotificationSourceIds(
                OpportunityPredictionHistoryReplayService.SOURCE_ENTITY_TYPE,
                NotificationType.OPPORTUNITY_PREDICTION.name(), List.of(1L)))
                .thenReturn(Set.of());
        when(recipientResolver.resolve()).thenReturn(snapshot(Set.of(30L), List.of(10L, 11L)));

        var outcome = service.run(RUN_DATE, CUTOFF,
                OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW);

        assertThat(outcome.candidates()).isEqualTo(1);
        assertThat(outcome.projectsWithActiveManager()).isEqualTo(1);
        assertThat(outcome.eligible()).isEqualTo(1);
        assertThat(outcome.targetRecipientRelations()).isEqualTo(3);
        assertThat(outcome.created()).isZero();
        verify(systemActorResolver, never()).resolveCached();
        verify(transactionService, never()).createNotificationIfAbsent(any(), any());
    }

    @Test
    @DisplayName("execute 忽略旧送达状态并向当前经理与核心角色完整去重补发")
    void executeCreatesIndependentFullReplay() {
        List<OpportunityPredictionReminderLog> logs = List.of(
                reminder(1L, LocalDate.of(2026, 8, 1)),
                reminder(2L, LocalDate.of(2026, 8, 2)));
        when(reminderLogRepository
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
                        CUTOFF, RUN_DATE.atStartOfDay())).thenReturn(logs);
        when(projectRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(project(1L, 10L), project(2L, 30L)));
        when(transactionService.findExistingNotificationSourceIds(
                OpportunityPredictionHistoryReplayService.SOURCE_ENTITY_TYPE,
                NotificationType.OPPORTUNITY_PREDICTION.name(), List.of(1L, 2L)))
                .thenReturn(Set.of());
        when(recipientResolver.resolve()).thenReturn(snapshot(Set.of(10L, 30L), List.of(10L, 11L)));
        when(systemActorResolver.resolveCached()).thenReturn(SYSTEM_ACTOR);
        when(transactionService.createNotificationIfAbsent(any(), eq(SYSTEM_ACTOR))).thenReturn(true);

        var outcome = service.run(RUN_DATE, CUTOFF,
                OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE);

        assertThat(outcome.created()).isEqualTo(2);
        assertThat(outcome.targetRecipientRelations()).isEqualTo(5);
        assertThat(outcome.projectsWithActiveManager()).isEqualTo(2);
        ArgumentCaptor<CreateNotificationRequest> requestCaptor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(transactionService, org.mockito.Mockito.times(2))
                .createNotificationIfAbsent(requestCaptor.capture(), eq(SYSTEM_ACTOR));
        assertThat(requestCaptor.getAllValues()).allSatisfy(request -> {
            assertThat(request.type()).isEqualTo(NotificationType.OPPORTUNITY_PREDICTION.name());
            assertThat(request.sourceEntityType())
                    .isEqualTo(OpportunityPredictionHistoryReplayService.SOURCE_ENTITY_TYPE);
            assertThat(request.title()).contains("历史补发");
            assertThat(request.payload()).containsEntry("historicalReplay", true)
                    .containsEntry("replayRunDate", RUN_DATE.toString())
                    .containsEntry("replayCutoff", CUTOFF.toString());
        });
        assertThat(requestCaptor.getAllValues().get(0).recipientUserIds())
                .containsExactly(10L, 11L);
        assertThat(requestCaptor.getAllValues().get(1).recipientUserIds())
                .containsExactly(10L, 11L, 30L);
    }

    @Test
    @DisplayName("已补发项目幂等跳过，项目缺失和无实时收件人分别计数")
    void skipsExistingMissingAndRecipientlessProjects() {
        List<OpportunityPredictionReminderLog> logs = List.of(
                reminder(1L, CUTOFF), reminder(2L, CUTOFF), reminder(3L, CUTOFF));
        when(reminderLogRepository
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
                        CUTOFF, RUN_DATE.atStartOfDay())).thenReturn(logs);
        when(projectRepository.findAllById(List.of(1L, 2L, 3L)))
                .thenReturn(List.of(project(1L, 10L), project(2L, 20L)));
        when(transactionService.findExistingNotificationSourceIds(
                OpportunityPredictionHistoryReplayService.SOURCE_ENTITY_TYPE,
                NotificationType.OPPORTUNITY_PREDICTION.name(), List.of(1L, 2L, 3L)))
                .thenReturn(Set.of(1L));
        when(recipientResolver.resolve()).thenReturn(snapshot(Set.of(), List.of()));

        var outcome = service.run(RUN_DATE, CUTOFF,
                OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW);

        assertThat(outcome.alreadyReplayed()).isEqualTo(1);
        assertThat(outcome.noRecipients()).isEqualTo(1);
        assertThat(outcome.missingProjects()).isEqualTo(1);
        assertThat(outcome.eligible()).isZero();
    }

    @Test
    @DisplayName("单项目写入失败被隔离，后续项目继续执行")
    void isolatesSingleProjectFailure() {
        List<OpportunityPredictionReminderLog> logs = List.of(reminder(1L, CUTOFF), reminder(2L, CUTOFF));
        when(reminderLogRepository
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
                        CUTOFF, RUN_DATE.atStartOfDay())).thenReturn(logs);
        when(projectRepository.findAllById(List.of(1L, 2L)))
                .thenReturn(List.of(project(1L, 10L), project(2L, 20L)));
        when(transactionService.findExistingNotificationSourceIds(any(), any(), any()))
                .thenReturn(Set.of());
        when(recipientResolver.resolve()).thenReturn(snapshot(Set.of(), List.of(10L)));
        when(systemActorResolver.resolveCached()).thenReturn(SYSTEM_ACTOR);
        when(transactionService.createNotificationIfAbsent(any(), eq(SYSTEM_ACTOR)))
                .thenThrow(new IllegalStateException("first failed"))
                .thenReturn(true);

        var outcome = service.run(RUN_DATE, CUTOFF,
                OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE);

        assertThat(outcome.created()).isEqualTo(1);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.targetRecipientRelations()).isEqualTo(1);
    }

    @Test
    @DisplayName("OSS 实时查询失败时整轮终止且不写入")
    void abortsWhenLiveRecipientLookupFails() {
        OpportunityPredictionReminderLog log = reminder(1L, CUTOFF);
        when(reminderLogRepository
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(
                        CUTOFF, RUN_DATE.atStartOfDay())).thenReturn(List.of(log));
        when(projectRepository.findAllById(List.of(1L))).thenReturn(List.of(project(1L, 10L)));
        when(transactionService.findExistingNotificationSourceIds(any(), any(), any()))
                .thenReturn(Set.of());
        when(recipientResolver.resolve()).thenReturn(
                new OpportunityPredictionRecipientResolver.RecipientSnapshot(
                        Set.of(), List.of(), 20, 0, false));

        assertThatThrownBy(() -> service.run(RUN_DATE, CUTOFF,
                OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("OSS");

        verify(systemActorResolver, never()).resolveCached();
        verify(transactionService, never()).createNotificationIfAbsent(any(), any());
    }

    @Test
    @DisplayName("截止日晚于执行日时失败关闭且不查询数据")
    void rejectsCutoffAfterRunDate() {
        assertThatThrownBy(() -> service.run(RUN_DATE, RUN_DATE.plusDays(1),
                OpportunityPredictionHistoryReplayService.ReplayMode.EXECUTE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cutoff");

        verify(reminderLogRepository, never())
                .findByPredictedBidDateLessThanEqualAndNotifiedAtBeforeOrderByProjectIdAsc(any(), any());
    }

    private OpportunityPredictionReminderLog reminder(Long projectId, LocalDate predicted) {
        return OpportunityPredictionReminderLog.builder()
                .projectId(projectId)
                .predictedBidDate(predicted)
                .purchaserName("招标主体" + projectId)
                .notifiedAt(RUN_DATE.minusDays(1).atTime(9, 0))
                .build();
    }

    private Project project(Long id, Long managerId) {
        return Project.builder()
                .id(id)
                .name("历史项目" + id)
                .tenderId(100L + id)
                .managerId(managerId)
                .build();
    }

    private OpportunityPredictionRecipientResolver.RecipientSnapshot snapshot(
            Set<Long> activeUserIds, List<Long> roleRecipientIds) {
        return new OpportunityPredictionRecipientResolver.RecipientSnapshot(
                activeUserIds, roleRecipientIds, 20, 20);
    }
}
