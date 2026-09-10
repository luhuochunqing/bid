// Input: 真实 Spring 代理、H2 JPA、mock 通知服务
// Output: 商机预测提醒短事务提交/回滚与原子领取证据
// Pos: backend/src/test/java/com/xiyu/bid/workbench/service/ - 事务边界集成测试
package com.xiyu.bid.workbench.service;

import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.test.AbstractTransactionBoundaryTest;
import com.xiyu.bid.workbench.entity.OpportunityPredictionReminderLog;
import com.xiyu.bid.workbench.repository.OpportunityPredictionReminderLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ActiveProfiles("test")
@DisplayName("商机预测提醒事务边界")
class OpportunityPredictionReminderTransactionBoundaryTest extends AbstractTransactionBoundaryTest {

    @Autowired
    private OpportunityPredictionReminderTransactionService transactionService;

    @Autowired
    private OpportunityPredictionReminderLogRepository repository;

    @MockBean
    private NotificationApplicationService notificationApplicationService;

    @BeforeEach
    void clean() {
        repository.deleteAll();
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.validWithId(1L));
    }

    @Test
    @Transactional(propagation = Propagation.NEVER)
    @DisplayName("提醒与初始领取在独立事务提交，方法返回后可重新查询")
    void createNotificationAndClaim_commitsBeforeReturning() {
        LocalDateTime claimedAt = LocalDateTime.of(2026, 9, 10, 9, 0);

        transactionService.createNotificationAndClaim(
                pendingLog(983L), notificationRequest(983L), 999L, "claim-a", claimedAt);

        OpportunityPredictionReminderLog stored = repository.findByProjectId(983L).orElseThrow();
        assertThat(stored.getEventPublishedAt()).isNull();
        assertThat(stored.getEventPublishClaimToken()).isEqualTo("claim-a");
        assertThat(stored.getEventPublishClaimedAt()).isEqualTo(claimedAt);
    }

    @Test
    @Transactional(propagation = Propagation.NEVER)
    @DisplayName("通知校验失败时提醒日志随短事务回滚")
    void createNotificationAndClaim_rejectedNotificationRollsBack() {
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.invalid("INVALID", "invalid"));

        assertThrows(OpportunityPredictionReminderTransactionService.NotificationRejectedException.class,
                () -> transactionService.createNotificationAndClaim(
                        pendingLog(984L), notificationRequest(984L), 999L,
                        "claim-rejected", LocalDateTime.now()));

        assertThat(repository.findByProjectId(984L)).isEmpty();
    }

    @Test
    @Transactional(propagation = Propagation.NEVER)
    @DisplayName("同一待发事件同一时刻只有一个实例能领取，成功状态真实提交")
    void claimAndComplete_isAtomicAndPersistent() {
        repository.saveAndFlush(pendingLog(985L));
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 9, 0);

        assertThat(transactionService.claimPendingEvent(
                985L, "pod-a", now, now.minusMinutes(10))).isTrue();
        assertThat(transactionService.claimPendingEvent(
                985L, "pod-b", now.plusSeconds(1), now.minusMinutes(9))).isFalse();
        assertThat(transactionService.completeEventPublish(
                985L, "pod-a", now.plusSeconds(2))).isTrue();

        OpportunityPredictionReminderLog stored = repository.findByProjectId(985L).orElseThrow();
        assertThat(stored.getEventPublishedAt()).isEqualTo(now.plusSeconds(2));
        assertThat(stored.getEventPublishClaimToken()).isNull();
        assertThat(stored.getEventPublishClaimedAt()).isNull();
    }

    @Test
    @Transactional(propagation = Propagation.NEVER)
    @DisplayName("失败释放及超时领取都允许后续实例恢复")
    void releasedOrStaleClaim_canBeRecovered() {
        repository.saveAndFlush(pendingLog(986L));
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 9, 0);

        assertThat(transactionService.claimPendingEvent(
                986L, "pod-a", now, now.minusMinutes(10))).isTrue();
        transactionService.releaseEventClaim(986L, "pod-a");
        assertThat(transactionService.claimPendingEvent(
                986L, "pod-b", now.plusMinutes(1), now.minusMinutes(9))).isTrue();
        assertThat(transactionService.claimPendingEvent(
                986L, "pod-c", now.plusMinutes(12), now.plusMinutes(2))).isTrue();
    }

    @Test
    @Transactional(propagation = Propagation.NEVER)
    @DisplayName("后续项目事务失败不会回滚前一项目已提交的事件状态")
    void laterProjectFailure_doesNotRollbackEarlierCommit() {
        repository.saveAndFlush(pendingLog(987L));
        LocalDateTime now = LocalDateTime.of(2026, 9, 10, 9, 0);
        assertThat(transactionService.claimPendingEvent(
                987L, "first", now, now.minusMinutes(10))).isTrue();
        assertThat(transactionService.completeEventPublish(
                987L, "first", now.plusSeconds(1))).isTrue();

        when(notificationApplicationService.createNotification(any(), any()))
                .thenThrow(new IllegalStateException("later project failed"));
        assertThrows(IllegalStateException.class, () -> transactionService.createNotificationAndClaim(
                pendingLog(988L), notificationRequest(988L), 999L, "second", now.plusSeconds(2)));

        assertThat(repository.findByProjectId(987L).orElseThrow().getEventPublishedAt())
                .isEqualTo(now.plusSeconds(1));
        assertThat(repository.findByProjectId(988L)).isEmpty();
    }

    private OpportunityPredictionReminderLog pendingLog(Long projectId) {
        return OpportunityPredictionReminderLog.builder()
                .projectId(projectId)
                .predictedBidDate(LocalDate.of(2026, 6, 30))
                .notifiedAt(LocalDateTime.of(2026, 9, 10, 9, 0))
                .build();
    }

    private CreateNotificationRequest notificationRequest(Long projectId) {
        return new CreateNotificationRequest(
                "OPPORTUNITY_PREDICTION", "Project", projectId,
                "title", "body", java.util.Map.of("projectId", projectId), List.of(10L));
    }
}
