package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link BudgetAlertDispatchService#createAlert} 通知触发逻辑单元测试。
 *
 * <p>P1-3 改造后验证：调用 {@link AlertNotificationOrchestrator#createAndNotifyIfNew}
 * 模板方法，payload 包含 projectId/projectName/expenseRatio/totalExpense/budget/targetUrl 字段。</p>
 *
 * <p>测试路径：{@code dispatch(rule)} → 遍历活动项目 → 计算费用占比 →
 * 命中告警条件 → {@code createAlert(rule, project, expenseRatio, totalExpense, budget)}。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("BudgetAlertDispatchService 新建告警通知触发测试")
class BudgetAlertDispatchServiceNotificationTest {

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
    @DisplayName("新建告警(created=true)时调用 createAndNotifyIfNew，payload 含正确字段")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(projectRepository.findActiveProjects()).thenReturn(List.of(project));
        // P0-2: 批量加载 Tender，使用 findAllById
        when(tenderRepository.findAllById(Set.of(10L))).thenReturn(List.of(tender));
        // 已用费用 8000 → 占比 80% (> 70 触发)
        when(expenseRepository.sumAmountByProjectIdIn(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("8000")}));

        AlertHistory savedHistory = AlertHistory.builder()
                .id(301L)
                .ruleId(2L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("项目 测试项目 费用已达到预算的 80.00%")
                .relatedId("Project:1")
                .resolved(false)
                .build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), eq(budgetRule), any(Map.class)))
                .thenReturn(new AlertHistoryCreateResult(savedHistory, true));

        budgetAlertDispatchService.dispatch(budgetRule);

        // 捕获并验证 createAndNotifyIfNew 的 payload
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(budgetRule), payloadCaptor.capture());

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
    @DisplayName("复用已有告警(created=false)时仍调用 createAndNotifyIfNew（由模板方法决定是否发通知）")
    void shouldStillCallCreateAndNotifyIfNewWhenAlertReused() {
        when(projectRepository.findActiveProjects()).thenReturn(List.of(project));
        // P0-2: 批量加载 Tender，使用 findAllById
        when(tenderRepository.findAllById(Set.of(10L))).thenReturn(List.of(tender));
        when(expenseRepository.sumAmountByProjectIdIn(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("8000")}));

        AlertHistory existingHistory = AlertHistory.builder()
                .id(401L)
                .ruleId(2L)
                .level(AlertHistory.AlertLevel.HIGH)
                .message("已有预算告警")
                .relatedId("Project:1")
                .resolved(false)
                .build();
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), eq(budgetRule), any(Map.class)))
                .thenReturn(new AlertHistoryCreateResult(existingHistory, false));

        budgetAlertDispatchService.dispatch(budgetRule);

        // P1-3: createAndNotifyIfNew 是统一入口，无论新建/复用都会调用
        // 是否真正触发通知由 Orchestrator 内部按 created 决定
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(budgetRule), any(Map.class));
    }

    @Test
    @DisplayName("费用占比未超阈值时不调用 createAndNotifyIfNew")
    void shouldNotCreateAlertWhenExpenseRatioBelowThreshold() {
        when(projectRepository.findActiveProjects()).thenReturn(List.of(project));
        // P0-2: 批量加载 Tender，使用 findAllById
        when(tenderRepository.findAllById(Set.of(10L))).thenReturn(List.of(tender));
        // 已用费用 5000 → 占比 50% (<= 70 不触发)
        when(expenseRepository.sumAmountByProjectIdIn(List.of(1L)))
                .thenReturn(List.<Object[]>of(new Object[]{1L, new BigDecimal("5000")}));

        budgetAlertDispatchService.dispatch(budgetRule);

        // 验证未调用 createAndNotifyIfNew
        verify(alertNotificationOrchestrator, never())
                .createAndNotifyIfNew(any(), any(), any());
    }
}
