package com.xiyu.bid.resources.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
import com.xiyu.bid.alerts.service.AlertRuleProvisioningService;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.entity.Project;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScanDepositReturnTrackingAppService#scan()} 通知触发逻辑单元测试。
 *
 * <p>P1-3 / P1-4 / P1-6 / P1-11 改造后验证核心契约：
 * <ul>
 *   <li>使用 {@link AlertRuleProvisioningService#ensureRuleWithThresholdSync} 而非直接访问 AlertRuleRepository</li>
 *   <li>使用 {@link AlertNotificationOrchestrator#createAndNotifyIfNew} 模板方法（统一 create + dispatch）</li>
 *   <li>P1-6: 批量预加载 BidResultFetchResult 和 Project，消除循环内 N+1 查询</li>
 *   <li>P1-11: created=true 时执行 expense 副作用（recordReturnReminder + save）且计数 +1</li>
 *   <li>P1-11: created=false 时**不**执行 expense 副作用，计数为 0（避免重复提醒时也更新 reminder 时间）</li>
 * </ul>
 *
 * <p>注意：{@code reminderPolicy} 是内联 {@code new} 的纯核心对象，测试中用真实数据驱动其返回
 * shouldRemind=true（DUE_SOON 场景：expectedReturnDate 在 warnDays 内且当天未提醒过）。</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ScanDepositReturnTrackingAppService 通知触发单元测试")
class ScanDepositReturnTrackingAppServiceNotificationTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private BidResultFetchResultRepository bidResultFetchResultRepository;
    @Mock
    private AlertRuleProvisioningService alertRuleProvisioningService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;
    @Mock
    private SettingsService settingsService;
    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ScanDepositReturnTrackingAppService service;

    // 以下 mock 为测试辅助对象，非 service 依赖（不会被 @InjectMocks 注入）
    @Mock
    private Expense expense;
    @Mock
    private BidResultFetchResult fetchResult;
    @Mock
    private Project project;

    private AlertRule rule;
    private AlertHistory alertHistory;

    @BeforeEach
    void setUp() {
        rule = AlertRule.builder()
                .id(1L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("system")
                .build();

        // 构造 DUE_SOON 场景：状态 APPROVED + 已确认中标 + 应退日期在 warnDays(7) 内 + 未提醒过
        when(expense.getId()).thenReturn(100L);
        when(expense.getProjectId()).thenReturn(200L);
        when(expense.getStatus()).thenReturn(Expense.ExpenseStatus.APPROVED);
        when(expense.getExpectedReturnDate()).thenReturn(LocalDate.now().plusDays(3));
        when(expense.getLastReturnReminderAt()).thenReturn(null);

        // P1-6: batch query 返回 List，fetchResult 需 stub getProjectId 供 groupingBy 使用
        when(fetchResult.getProjectId()).thenReturn(200L);
        when(fetchResult.getResult()).thenReturn(BidResultFetchResult.Result.WON);
        when(project.getId()).thenReturn(200L);
        when(project.getName()).thenReturn("测试项目");

        alertHistory = AlertHistory.builder()
                .id(301L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("测试保证金退还提醒")
                .relatedId("DepositReturn:100::DUE_SOON")
                .resolved(false)
                .build();

        // settings: depositWarnDays=7
        SettingsResponse settings = SettingsResponse.builder()
                .systemConfig(SettingsResponse.SystemConfig.builder()
                        .depositWarnDays(7)
                        .build())
                .build();
        when(settingsService.getSettings()).thenReturn(settings);

        // P1-4: 共享 provisioning service（带 threshold sync）
        when(alertRuleProvisioningService.ensureRuleWithThresholdSync(
                eq(AlertRule.AlertType.DEPOSIT_RETURN), anyString(), anyInt())).thenReturn(rule);

        when(expenseRepository.findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                "保证金", Expense.ExpenseStatus.RETURNED))
                .thenReturn(List.of(expense));

        // P1-6: 批量查询替代 N+1
        when(bidResultFetchResultRepository.findByProjectIdsInAndStatus(
                eq(Set.of(200L)), eq(BidResultFetchResult.Status.CONFIRMED)))
                .thenReturn(List.of(fetchResult));

        when(projectRepository.findAllById(Set.of(200L)))
                .thenReturn(List.of(project));
    }

    @Test
    @DisplayName("新建告警(created=true)时触发通知，且执行 expense 副作用与计数")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, true));

        int reminded = service.scan();

        // 验证 createAndNotifyIfNew 被调用（统一入口）
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class));
        // P1-11: created=true 时执行 expense 副作用
        verify(expense).recordReturnReminder(any());
        verify(expenseRepository).save(expense);
        assertThat(reminded).isEqualTo(1);
    }

    @Test
    @DisplayName("复用已有告警(created=false)时不执行 expense 副作用，计数为 0")
    void shouldNotUpdateExpenseWhenAlertReused() {
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, false));

        int reminded = service.scan();

        // P1-3: createAndNotifyIfNew 是统一入口，无论新建/复用都会调用
        verify(alertNotificationOrchestrator)
                .createAndNotifyIfNew(any(AlertHistoryCreateRequest.class), eq(rule), any(Map.class));
        // P1-11: created=false 时不执行 expense 副作用（避免重复提醒时也更新 reminder 时间和计数）
        verify(expense, never()).recordReturnReminder(any());
        verify(expenseRepository, never()).save(expense);
        // P1-11: 计数为 0（仅新建告警才计入 reminded）
        assertThat(reminded).isEqualTo(0);
    }
}
