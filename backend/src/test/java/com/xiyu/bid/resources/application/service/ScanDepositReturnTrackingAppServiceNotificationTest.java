package com.xiyu.bid.resources.application.service;

import com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest;
import com.xiyu.bid.alerts.dto.AlertHistoryCreateResult;
import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.alerts.service.AlertNotificationOrchestrator;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScanDepositReturnTrackingAppService#scan()} 通知触发逻辑单元测试。
 *
 * <p>验证核心契约：
 * <ul>
 *   <li>created=true 时调用 {@link AlertNotificationOrchestrator#dispatchNotification}</li>
 *   <li>created=false 时不调用 dispatchNotification</li>
 *   <li>无论是否新建告警，expense 状态更新（recordReturnReminder + save）与计数均保留</li>
 *   <li>dispatchNotification 传入正确的 alertHistory 和 alertRule</li>
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
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertHistoryService alertHistoryService;
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

        when(fetchResult.getResult()).thenReturn(BidResultFetchResult.Result.WON);
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

        when(alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN))
                .thenReturn(List.of(rule));

        when(expenseRepository.findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                "保证金", Expense.ExpenseStatus.RETURNED))
                .thenReturn(List.of(expense));

        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                200L, BidResultFetchResult.Status.CONFIRMED))
                .thenReturn(Optional.of(fetchResult));

        when(projectRepository.findById(200L)).thenReturn(Optional.of(project));
    }

    @Test
    @DisplayName("新建告警(created=true)时触发通知，且保留 expense 副作用与计数")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, true));

        int reminded = service.scan();

        // 验证通知被触发，传入正确的 alertHistory 和 rule
        verify(alertNotificationOrchestrator).dispatchNotification(eq(alertHistory), eq(rule), any(Map.class));
        // 验证 expense 副作用保留：无论是否新建告警都执行状态更新
        verify(expense).recordReturnReminder(any());
        verify(expenseRepository).save(expense);
        assertThat(reminded).isEqualTo(1);
    }

    @Test
    @DisplayName("复用已有告警(created=false)时不触发通知，但保留 expense 副作用与计数")
    void shouldNotDispatchNotificationWhenAlertReused() {
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, false));

        int reminded = service.scan();

        // 验证通知未触发
        verify(alertNotificationOrchestrator, never()).dispatchNotification(any(), any(), any());
        // 验证 expense 副作用仍保留
        verify(expense).recordReturnReminder(any());
        verify(expenseRepository).save(expense);
        assertThat(reminded).isEqualTo(1);
    }
}
