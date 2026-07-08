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
import com.xiyu.bid.resources.dto.ExpenseDTO;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import com.xiyu.bid.resources.service.expense.ExpenseAccessGuard;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SendExpenseReturnReminderAppService#send} 通知触发逻辑单元测试。
 *
 * <p>验证核心契约：
 * <ul>
 *   <li>created=true 时调用 {@link AlertNotificationOrchestrator#dispatchNotification}，extraPayload 为 null</li>
 *   <li>created=false 时不调用 dispatchNotification</li>
 *   <li>无论是否新建告警，expense 状态更新（recordReturnReminder + save）均保留</li>
 *   <li>dispatchNotification 传入正确的 alertHistory 和 alertRule</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SendExpenseReturnReminderAppService 通知触发单元测试")
class SendExpenseReturnReminderAppServiceNotificationTest {

    private static final Long EXPENSE_ID = 100L;
    private static final Long PROJECT_ID = 200L;

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private BidResultFetchResultRepository bidResultFetchResultRepository;
    @Mock
    private ProjectRepository projectRepository;
    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertHistoryService alertHistoryService;
    @Mock
    private AlertNotificationOrchestrator alertNotificationOrchestrator;
    @Mock
    private SettingsService settingsService;
    @Mock
    private ExpenseAccessGuard accessGuard;

    @InjectMocks
    private SendExpenseReturnReminderAppService service;

    // 以下 mock 为测试辅助对象，非 service 依赖
    @Mock
    private Expense expense;
    @Mock
    private BidResultFetchResult fetchResult;
    @Mock
    private Project project;

    private AlertRule rule;
    private AlertHistory alertHistory;
    private Expense savedExpense;

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

        // mock Expense：状态 APPROVED + 可退还 + 已确认中标 + 应退日期在未来
        when(expense.getProjectId()).thenReturn(PROJECT_ID);
        when(expense.isReturnable()).thenReturn(true);
        when(expense.getStatus()).thenReturn(Expense.ExpenseStatus.APPROVED);
        when(expense.getExpectedReturnDate()).thenReturn(LocalDate.now().plusDays(3));
        when(expense.getLastReturnReminderAt()).thenReturn(null);

        when(fetchResult.getResult()).thenReturn(BidResultFetchResult.Result.WON);
        when(project.getName()).thenReturn("测试项目");

        // 真实 Expense 对象作为 save 返回值，避免 ResourceResponseMapper.toDto 在 mock 上 NPE
        savedExpense = Expense.builder()
                .id(EXPENSE_ID)
                .projectId(PROJECT_ID)
                .category(Expense.ExpenseCategory.MATERIAL)
                .expenseType("保证金")
                .amount(BigDecimal.valueOf(1000))
                .date(LocalDate.now())
                .createdBy("tester")
                .status(Expense.ExpenseStatus.APPROVED)
                .expectedReturnDate(LocalDate.now().plusDays(3))
                .build();

        alertHistory = AlertHistory.builder()
                .id(301L)
                .ruleId(1L)
                .level(AlertHistory.AlertLevel.MEDIUM)
                .message("手动发起保证金退还跟进")
                .relatedId("DepositReturn:100:" + LocalDate.now().plusDays(3))
                .resolved(false)
                .build();

        // settings: depositWarnDays=7（使 resolveWarnDays()=7 匹配 rule.threshold，syncRuleThreshold 直接返回 rule）
        SettingsResponse settings = SettingsResponse.builder()
                .systemConfig(SettingsResponse.SystemConfig.builder()
                        .depositWarnDays(7)
                        .build())
                .build();
        when(settingsService.getSettings()).thenReturn(settings);

        when(expenseRepository.findById(EXPENSE_ID)).thenReturn(Optional.of(expense));
        when(alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN))
                .thenReturn(List.of(rule));
        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                PROJECT_ID, BidResultFetchResult.Status.CONFIRMED))
                .thenReturn(Optional.of(fetchResult));
        when(projectRepository.findById(PROJECT_ID)).thenReturn(Optional.of(project));
        when(expenseRepository.save(expense)).thenReturn(savedExpense);
    }

    @Test
    @DisplayName("新建告警(created=true)时触发通知(extraPayload=null)，且保留 expense 副作用")
    void shouldDispatchNotificationWhenAlertCreated() {
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, true));

        ExpenseDTO result = service.send(EXPENSE_ID, "李总", "请跟进退还");

        // 验证通知被触发，传入正确的 alertHistory/rule，且 extraPayload 为 null
        verify(alertNotificationOrchestrator).dispatchNotification(eq(alertHistory), eq(rule), isNull());
        // 验证 expense 副作用保留
        verify(expense).recordReturnReminder(any());
        verify(expenseRepository).save(expense);
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("复用已有告警(created=false)时不触发通知，但保留 expense 副作用")
    void shouldNotDispatchNotificationWhenAlertReused() {
        when(alertHistoryService.createAlertHistoryIfAbsent(any(AlertHistoryCreateRequest.class)))
                .thenReturn(new AlertHistoryCreateResult(alertHistory, false));

        ExpenseDTO result = service.send(EXPENSE_ID, "李总", "请跟进退还");

        // 验证通知未触发
        verify(alertNotificationOrchestrator, never()).dispatchNotification(any(), any(), any());
        // 验证 expense 副作用仍保留
        verify(expense).recordReturnReminder(any());
        verify(expenseRepository).save(expense);
        assertThat(result).isNotNull();
    }
}
