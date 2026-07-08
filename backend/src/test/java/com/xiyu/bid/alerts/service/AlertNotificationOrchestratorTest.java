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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    @DisplayName("notificationApplicationService 抛异常 → 不重抛")
    void shouldNotPropagateExceptionFromNotificationService() {
        AlertHistory history = buildHistory();
        AlertRule rule = buildRule();
        when(notificationRecipientResolver.getUserIdsByRoleCodes(any()))
                .thenReturn(List.of(100L));
        when(systemActorResolver.resolveCached()).thenReturn(1L);
        when(notificationApplicationService.createNotification(any(), any()))
                .thenThrow(new RuntimeException("DB down"));

        assertDoesNotThrow(() -> orchestrator.dispatchNotification(history, rule, Map.of("k", "v")));
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
}
