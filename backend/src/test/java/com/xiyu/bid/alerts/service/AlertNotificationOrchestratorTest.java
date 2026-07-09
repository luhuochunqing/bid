// Input: AlertHistory + AlertRule + extraPayload（编排器输入）
// Output: 对编排器行为的验证（mock 三个 Spring 依赖，真实调用纯核心层）
// Pos: Test/alerts/service - 告警通知编排器单元测试
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.notification.core.DispatchResult;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.notification.service.NotificationRecipientResolver;
import com.xiyu.bid.repository.ProjectRepository;
import io.sentry.Sentry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 告警通知编排器单元测试。
 *
 * <p>纯核心层（{@link com.xiyu.bid.alerts.domain.AlertMessagePolicy} /
 * {@link com.xiyu.bid.alerts.domain.AlertRecipientPolicy}）真实调用，不 mock。
 * 三个 Spring 依赖（NotificationApplicationService /
 * NotificationRecipientResolver / SystemActorResolver）用 Mockito mock。</p>
 *
 * <p>测试覆盖：
 * <ol>
 *   <li>正常流程：接收人非空、systemActor 非空 → 调 createNotification 一次</li>
 *   <li>接收人空列表 → 不调 createNotification</li>
 *   <li>systemActor 为 null → 不调 createNotification</li>
 *   <li>notificationApplicationService 抛异常 → 不重抛</li>
 *   <li>验证传递的 NotificationType 正确</li>
 *   <li>extraPayload 为 null 时也能正常工作</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class AlertNotificationOrchestratorTest {

    @Mock
    private NotificationApplicationService notificationApplicationService;
    @Mock
    private NotificationRecipientResolver notificationRecipientResolver;
    @Mock
    private SystemActorResolver systemActorResolver;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AlertHistoryService alertHistoryService;

    @InjectMocks
    private AlertNotificationOrchestrator orchestrator;

    private AlertHistory buildHistory() {
        return AlertHistory.builder()
                .id(1L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("测试告警")
                .relatedId("Project:123")
                .resolved(false)
                .build();
    }

    private AlertRule buildRule() {
        return AlertRule.builder()
                .id(1L)
                .name("测试规则")
                .type(AlertRule.AlertType.DEADLINE)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("system")
                .build();
    }

    @Test
    @DisplayName("正常流程：接收人非空且 systemActor 非空 → 调用 createNotification 一次")
    void shouldCallCreateNotificationWhenRecipientsAndActorPresent() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L, 200L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        when(notificationApplicationService.createNotification(any(), eq(1L)))
                .thenReturn(DispatchResult.validWithId(99L));

        orchestrator.dispatchNotification(history, rule, Map.of("k", "v"));

        verify(notificationApplicationService).createNotification(any(), eq(1L));
    }

    @Test
    @DisplayName("接收人空列表 → 不调 createNotification")
    void shouldSkipCreateNotificationWhenRecipientsEmpty() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of());

        orchestrator.dispatchNotification(history, rule, Map.of("k", "v"));

        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("systemActor 为 null → 不调 createNotification")
    void shouldSkipCreateNotificationWhenSystemActorNull() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L));
        when(systemActorResolver.resolveCached()).thenReturn(null);

        orchestrator.dispatchNotification(history, rule, Map.of("k", "v"));

        verify(notificationApplicationService, never()).createNotification(any(), any());
    }

    @Test
    @DisplayName("notificationApplicationService 抛 RuntimeException → 不重抛，且上报 Sentry（CO-564）")
    void shouldNotPropagateRuntimeExceptionAndReportToSentry() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        RuntimeException npe = new NullPointerException("编程 bug");
        when(notificationApplicationService.createNotification(any(), any()))
                .thenThrow(npe);

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            assertDoesNotThrow(() -> orchestrator.dispatchNotification(history, rule, Map.of("k", "v")));
            // CO-564: 编程 bug 级异常必须上报 Sentry，让运维可主动发现告警链路静默失败
            sentry.verify(() -> Sentry.captureException(npe));
        }
    }

    @Test
    @DisplayName("notificationApplicationService 抛 DataAccessException → 不重抛，且不上报 Sentry（DB 故障降级）")
    void shouldNotPropagateDataAccessExceptionAndNotReportToSentry() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        DataAccessException dbEx = new org.springframework.dao.QueryTimeoutException("DB 查询超时");
        when(notificationApplicationService.createNotification(any(), any()))
                .thenThrow(dbEx);

        try (MockedStatic<Sentry> sentry = mockStatic(Sentry.class)) {
            assertDoesNotThrow(() -> orchestrator.dispatchNotification(history, rule, Map.of("k", "v")));
            // DB 故障另有健康监控，不上报 Sentry（避免噪声）
            sentry.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("传递的 NotificationType 正确：DEADLINE 规则 → type=\"DEADLINE\"")
    void shouldPassCorrectNotificationType() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.validWithId(99L));

        orchestrator.dispatchNotification(history, rule, Map.of("k", "v"));

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(captor.capture(), eq(1L));
        // 骨架返回 "DEADLINE"；另一个 agent 覆盖实现后需同步更新此断言
        assertEquals("DEADLINE", captor.getValue().type());
    }

    @Test
    @DisplayName("extraPayload 为 null 时也能正常工作")
    void shouldWorkWithNullExtraPayload() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.validWithId(99L));

        assertDoesNotThrow(() -> orchestrator.dispatchNotification(history, rule, null));
        verify(notificationApplicationService).createNotification(any(), eq(1L));
    }

    /**
     * CO-546: extraPayload 含 custodianId 时，CA 保管员应被加入通知接收人列表。
     *
     * <p>定时扫描路径此前仅通知角色广播接收人（投标管理员/投标组长），缺少 CA 保管员。
     * 通过 extraPayload 携带 custodianId，dispatchNotification 应将其与角色接收人合并，
     * 保证 CA 保管员与 returnBorrow 路径一致地收到到期预警。</p>
     */
    @Test
    @DisplayName("CO-546: extraPayload 含 custodianId → 保管员加入接收人列表")
    void shouldAddCustodianToRecipientsWhenCustodianIdInPayload() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L, 200L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        when(notificationApplicationService.createNotification(any(), any()))
                .thenReturn(DispatchResult.validWithId(99L));

        orchestrator.dispatchNotification(history, rule, Map.of("custodianId", 99L));

        ArgumentCaptor<CreateNotificationRequest> captor =
                ArgumentCaptor.forClass(CreateNotificationRequest.class);
        verify(notificationApplicationService).createNotification(captor.capture(), eq(1L));
        List<Long> recipients = captor.getValue().recipientUserIds();
        assertTrue(recipients.contains(99L),
                "CA 保管员(99L) 必须在接收人列表中: " + recipients);
        assertTrue(recipients.contains(100L),
                "角色广播接收人(100L) 必须保留: " + recipients);
    }
}
