package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertRuleExecutionService#createAlert} 通知触发逻辑单元测试。
 *
 * <p>验证：调 {@code createAlertHistoryIfAbsent} 后，仅在 {@code created=true} 时
 * 调用 {@link AlertNotificationOrchestrator#dispatchNotification}。</p>
 *
 * <p>测试路径：{@code execute(rule)} → {@code checkDeadlineAlert(rule)} →
 * {@code createAlert(rule, tenderId, "Tender", message)}。
 * 使用 DEADLINE 类型规则，mock tenderRepository 返回一条截止日期临近的标讯。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertRuleExecutionService 新建告警通知触发测试")
class AlertRuleExecutionServiceNotificationTest {

    @Mock
    private AlertHistoryService alertHistoryService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private ProjectDocumentRepository projectDocumentRepository;

    @InjectMocks
    private AlertRuleExecutionService alertRuleExecutionService;

    /** 测试用 DEADLINE 规则：截止前 ≤ 7 天触发 */
    private AlertRule deadlineRule;
    /** 测试用标讯：截止日期 = 当前 + 3 天（命中 ≤ 7 天条件） */
    private Tender tender;

    @BeforeEach
    void setUp() {
        deadlineRule = AlertRule.builder()
                .id(1L)
                .name("截止日期提醒")
                .type(AlertRule.AlertType.DEADLINE)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("system")
                .build();

        tender = Tender.builder()
                .id(10L)
                .title("测试标讯")
                .status(Tender.Status.TRACKING)
                .deadline(LocalDateTime.now().plusDays(3))
                .build();
    }

    @Test
    @DisplayName("新建告警(created=true)时调用 dispatchNotification，payload 为 null")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any()))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        alertRuleExecutionService.execute(deadlineRule);

        // 验证调用了 createAlertHistoryIfAbsent
        verify(alertHistoryService).createAlertHistoryIfAbsent(any());
        // 验证调用了 dispatchNotification，且 extraPayload 为 null
        verify(alertNotificationOrchestrator)
                .dispatchNotification(eq(savedHistory), eq(deadlineRule), isNull());
    }

    @Test
    @DisplayName("复用已有告警(created=false)时不调用 dispatchNotification")
    void shouldNotDispatchNotificationWhenAlertReused() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory existingHistory = AlertHistory.builder()
                .id(201L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("已有截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any()))
                .thenReturn(new AlertHistoryCreateResult(existingHistory, false));

        alertRuleExecutionService.execute(deadlineRule);

        // 验证调用了 createAlertHistoryIfAbsent
        verify(alertHistoryService).createAlertHistoryIfAbsent(any());
        // 验证未调用 dispatchNotification
        verify(alertNotificationOrchestrator, never())
                .dispatchNotification(any(), any(), any());
    }

    @Test
    @DisplayName("新建告警时 request 中 relatedId 格式为 'Tender:<id>'，level 按 threshold 计算")
    void shouldUseCorrectRelatedIdFormat() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(102L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        ArgumentCaptor<com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest.class);
        when(alertHistoryService.createAlertHistoryIfAbsent(requestCaptor.capture()))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        alertRuleExecutionService.execute(deadlineRule);

        com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest captured = requestCaptor.getValue();
        assertThat(captured.getRelatedId()).isEqualTo("Tender:10");
        assertThat(captured.getRuleId()).isEqualTo(1L);
        // calculateSeverity: threshold=7 → 7<=1?CRITICAL : 7<=3?HIGH : MEDIUM → MEDIUM 级别
        assertThat(captured.getLevel()).isEqualTo(AlertHistory.AlertLevel.MEDIUM);
        // 验证 DEADLINE 走 null payload 分支
        verify(alertNotificationOrchestrator)
                .dispatchNotification(any(), any(), isNull());
    }
}
