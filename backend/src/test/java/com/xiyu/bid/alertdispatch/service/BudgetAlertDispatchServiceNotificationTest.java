package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Tender;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TenderRepository;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetAlertDispatchService#createAlert} 通知触发逻辑单元测试。
 *
 * <p>验证：调 {@code createAlertHistoryIfAbsent} 后，仅在 {@code created=true} 时
 * 调用 {@link AlertNotificationOrchestrator#dispatchNotification}，且 payload 包含
 * projectId/projectName/expenseRatio/totalExpense/budget/targetUrl 字段。</p>
 *
 * <p>测试路径：{@code dispatch(rule)} → 遍历活动项目 → 计算费用占比 →
 * 命中告警条件 → {@code createAlert(rule, project, expenseRatio, totalExpense, budget)}。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetAlertDispatchService 新建告警通知触发测试")
class BudgetAlertDispatchServiceNotificationTest {

    @Mock
    private AlertHistoryService alertHistoryService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private TenderRepository tenderRepository;
    @Mock
    private ExpenseRepository expenseRepository;

    @InjectMocks
    private BudgetAlertDispatchService budgetAlertDispatchService;

    /** 测试用预算规则：费用占比 > 70% 触发 */
    private AlertRule budgetRule;
    /** 测试用项目 */
    private Project project;
    /** 测试用标讯（含预算） */
    private Tender tender;

    @BeforeEach
    void setUp() {
        budgetRule = AlertRule.builder()
                .id(2L)
                .name("预算超支提醒")
                .type(AlertRule.AlertType.BUDGET)
                .condition(AlertRule.ConditionType.GREATER_THAN)
                .threshold(BigDecimal.valueOf(70))
                .enabled(true)
                .createdBy("system")
                .build();

        project = Project.builder()
                .id(1L)
                .name("测试项目")
                .tenderId(10L)
                .managerId(100L)
                .status(Project.Status.BIDDING)
                .build();

        tender = Tender.builder()
                .id(10L)
                .title("测试标讯")
                .budget(new BigDecimal("10000"))
                .build();
    }

    @Test
    @DisplayName("新建告警(created=true)时调用 dispatchNotification，payload 含正确字段")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(projectRepository.findActiveProjects()).thenReturn(List.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        // 已用费用 8000 → 占比 80% (> 70 触发)
        when(expenseRepository.sumAmountByProjectId(1L)).thenReturn(new BigDecimal("8000"));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(301L)
                .ruleId(2L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("项目 测试项目 费用已达到预算的 80.00%")
                .relatedId("Project:1")
                .resolved(false)
                .build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any()))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        budgetAlertDispatchService.dispatch(budgetRule);

        // 验证调用了 createAlertHistoryIfAbsent
        verify(alertHistoryService).createAlertHistoryIfAbsent(any());

        // 捕获并验证 dispatchNotification 的 payload
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(alertNotificationOrchestrator)
                .dispatchNotification(eq(savedHistory), eq(budgetRule), payloadCaptor.capture());

        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).isNotNull();
        assertThat(payload.get("projectId")).isEqualTo(1L);
        assertThat(payload.get("projectName")).isEqualTo("测试项目");
        // BigDecimal 比较用 isEqualByComparingTo 避免 scale 差异
        assertThat((BigDecimal) payload.get("expenseRatio")).isEqualByComparingTo(new BigDecimal("80"));
        assertThat((BigDecimal) payload.get("totalExpense")).isEqualByComparingTo(new BigDecimal("8000"));
        assertThat((BigDecimal) payload.get("budget")).isEqualByComparingTo(new BigDecimal("10000"));
        assertThat(payload.get("targetUrl")).isEqualTo("/projects/1");
    }

    @Test
    @DisplayName("复用已有告警(created=false)时不调用 dispatchNotification")
    void shouldNotDispatchNotificationWhenAlertReused() {
        when(projectRepository.findActiveProjects()).thenReturn(List.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        when(expenseRepository.sumAmountByProjectId(1L)).thenReturn(new BigDecimal("8000"));

        AlertHistory existingHistory = AlertHistory.builder()
                .id(401L)
                .ruleId(2L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("已有预算告警")
                .relatedId("Project:1")
                .resolved(false)
                .build();
        when(alertHistoryService.createAlertHistoryIfAbsent(any()))
                .thenReturn(new AlertHistoryCreateResult(existingHistory, false));

        budgetAlertDispatchService.dispatch(budgetRule);

        // 验证调用了 createAlertHistoryIfAbsent
        verify(alertHistoryService).createAlertHistoryIfAbsent(any());
        // 验证未调用 dispatchNotification
        verify(alertNotificationOrchestrator, never())
                .dispatchNotification(any(), any(), any());
    }

    @Test
    @DisplayName("费用占比未超阈值时不创建告警也不发通知")
    void shouldNotCreateAlertWhenExpenseRatioBelowThreshold() {
        when(projectRepository.findActiveProjects()).thenReturn(List.of(project));
        when(tenderRepository.findById(10L)).thenReturn(Optional.of(tender));
        // 已用费用 5000 → 占比 50% (<= 70 不触发)
        when(expenseRepository.sumAmountByProjectId(1L)).thenReturn(new BigDecimal("5000"));

        budgetAlertDispatchService.dispatch(budgetRule);

        // 验证未创建告警历史
        verify(alertHistoryService, never()).createAlertHistoryIfAbsent(any());
        // 验证未发通知
        verify(alertNotificationOrchestrator, never())
                .dispatchNotification(any(), any(), any());
    }
}
