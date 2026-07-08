package com.xiyu.bid.alerts.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
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
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AlertRuleExecutionService} 告警创建与通知触发单元测试。
 *
 * <p>验证：{@code createAlert} 正确委托 {@link AlertNotificationOrchestrator#createAndNotifyIfNew}
 * 模板方法，且传入的 request 和 extraPayload 符合预期。</p>
 *
 * <p>测试路径：{@code execute(rule)} → {@code checkDeadlineAlert(rule)} →
 * {@code createAlert(rule, tenderId, "Tender", message, extraPayload)} →
 * {@code alertNotificationOrchestrator.createAndNotifyIfNew(request, rule, extraPayload)}。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AlertRuleExecutionService 告警创建与模板方法委托测试")
class AlertRuleExecutionServiceNotificationTest {

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
    @DisplayName("DEADLINE 告警应委托 createAndNotifyIfNew 模板方法，传入正确的 request 和 extraPayload")
    void shouldDelegateToCreateAndNotifyIfNewTemplateMethod() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(101L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(any(), any(), any()))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        alertRuleExecutionService.execute(deadlineRule);

        // 验证调用了 createAndNotifyIfNew 模板方法
        verify(alertNotificationOrchestrator).createAndNotifyIfNew(any(), eq(deadlineRule), any());
    }

    @Test
    @DisplayName("request 中 relatedId 格式为 'Tender:<id>'，level 按 threshold 计算")
    void shouldUseCorrectRelatedIdFormatAndLevel() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(102L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        ArgumentCaptor<AlertHistoryCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(AlertHistoryCreateRequest.class);
        when(alertNotificationOrchestrator.createAndNotifyIfNew(requestCaptor.capture(), any(), any()))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        alertRuleExecutionService.execute(deadlineRule);

        AlertHistoryCreateRequest captured = requestCaptor.getValue();
        assertThat(captured.getRelatedId()).isEqualTo("Tender:10");
        assertThat(captured.getRuleId()).isEqualTo(1L);
        // calculateSeverity: threshold=7 → 7<=1?CRITICAL : 7<=3?HIGH : MEDIUM → MEDIUM 级别
        assertThat(captured.getLevel()).isEqualTo(AlertHistory.AlertLevel.MEDIUM);
    }

    @Test
    @DisplayName("DEADLINE extraPayload 应包含 targetUrl 和格式化日期")
    @SuppressWarnings("unchecked")
    void shouldIncludeTargetUrlAndFormattedDeadlineInExtraPayload() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(103L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        ArgumentCaptor<java.util.Map<String, Object>> payloadCaptor =
                ArgumentCaptor.forClass(java.util.Map.class);
        when(alertNotificationOrchestrator.createAndNotifyIfNew(any(), any(), payloadCaptor.capture()))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        alertRuleExecutionService.execute(deadlineRule);

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).isNotNull();
        assertThat(payload.get("targetUrl")).isEqualTo("/tender/detail/10");
        assertThat(payload.get("deadline")).isNotNull();
        // 格式应为 yyyy-MM-dd HH:mm（不含 T 分隔符）
        String deadlineStr = (String) payload.get("deadline");
        assertThat(deadlineStr).matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}");
    }

    @Test
    @DisplayName("created=false 时 createAndNotifyIfNew 仍被调用（模板方法内部决定是否 dispatch）")
    void shouldStillCallCreateAndNotifyIfNewWhenAlertReused() {
        when(tenderRepository.findByStatusIn(any())).thenReturn(List.of(tender));

        AlertHistory existingHistory = AlertHistory.builder()
                .id(201L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("已有截止日期告警")
                .relatedId("Tender:10")
                .resolved(false)
                .build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(any(), any(), any()))
                .thenReturn(new AlertHistoryCreateResult(existingHistory, false));

        alertRuleExecutionService.execute(deadlineRule);

        // 验证 createAndNotifyIfNew 仍被调用（模板方法内部根据 created 决定是否 dispatch）
        verify(alertNotificationOrchestrator).createAndNotifyIfNew(any(), any(), any());
    }
}
