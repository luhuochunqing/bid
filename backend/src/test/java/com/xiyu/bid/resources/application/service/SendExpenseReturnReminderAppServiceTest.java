package com.xiyu.bid.resources.application.service;

import com.xiyu.bid.alerts.entity.AlertHistory;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.repository.AlertRuleRepository;
import com.xiyu.bid.alerts.service.AlertHistoryService;
import com.xiyu.bid.bidresult.entity.BidResultFetchResult;
import com.xiyu.bid.bidresult.repository.BidResultFetchResultRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.resources.entity.Expense;
import com.xiyu.bid.resources.repository.ExpenseRepository;
import com.xiyu.bid.resources.service.expense.ExpenseAccessGuard;
import com.xiyu.bid.settings.dto.SettingsResponse;
import com.xiyu.bid.settings.service.SettingsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SendExpenseReturnReminderAppServiceTest {

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
    private SettingsService settingsService;
    @Mock
    private ExpenseAccessGuard accessGuard;

    @InjectMocks
    private SendExpenseReturnReminderAppService sendExpenseReturnReminderAppService;

    @Test
    @DisplayName("手工提醒应要求已确认开标结果并更新最后提醒时间")
    void shouldSendManualReminderWhenExpenseIsEligible() {
        Expense expense = Expense.builder()
                .id(502L)
                .projectId(602L)
                .category(Expense.ExpenseCategory.OTHER)
                .expenseType("保证金")
                .status(Expense.ExpenseStatus.RETURN_REQUESTED)
                .expectedReturnDate(LocalDate.of(2026, 5, 1))
                .build();
        BidResultFetchResult result = BidResultFetchResult.builder()
                .projectId(602L)
                .result(BidResultFetchResult.Result.LOST)
                .status(BidResultFetchResult.Status.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(1))
                .build();
        AlertRule rule = AlertRule.builder()
                .id(42L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("tester")
                .build();

        when(expenseRepository.findById(502L)).thenReturn(Optional.of(expense));
        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                602L, BidResultFetchResult.Status.CONFIRMED)).thenReturn(Optional.of(result));
        when(alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN)).thenReturn(List.of(rule));
        when(projectRepository.findById(602L)).thenReturn(Optional.empty());
        when(alertHistoryService.createAlertHistory(any())).thenReturn(AlertHistory.builder().id(1L).build());
        when(expenseRepository.save(expense)).thenReturn(expense);

        sendExpenseReturnReminderAppService.send(502L, "finance-user", "财务手工提醒");

        ArgumentCaptor<com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest> captor =
                ArgumentCaptor.forClass(com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest.class);
        verify(accessGuard).assertCanAccessProject(602L);
        verify(alertHistoryService).createAlertHistory(captor.capture());
        verify(expenseRepository).save(expense);
        assertThat(expense.getLastReturnReminderAt()).isNotNull();
        assertThat(captor.getValue().getRelatedId()).isEqualTo("DepositReturn:502:2026-05-01");
    }

    @Test
    @DisplayName("手工提醒在缺少已确认开标结果时应拒绝")
    void shouldRejectManualReminderWithoutConfirmedBidResult() {
        Expense expense = Expense.builder()
                .id(503L)
                .projectId(603L)
                .category(Expense.ExpenseCategory.OTHER)
                .expenseType("保证金")
                .status(Expense.ExpenseStatus.APPROVED)
                .expectedReturnDate(LocalDate.of(2026, 5, 3))
                .build();

        when(expenseRepository.findById(503L)).thenReturn(Optional.of(expense));
        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                603L, BidResultFetchResult.Status.CONFIRMED)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sendExpenseReturnReminderAppService.send(503L, "finance-user", "备注"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Expense is not eligible for deposit return reminder");
        verify(accessGuard).assertCanAccessProject(603L);
    }

    @Test
    @DisplayName("手工提醒在系统配置缺失时应使用默认提前天数创建规则")
    void shouldCreateDefaultRuleWhenSystemConfigIsMissing() {
        Expense expense = Expense.builder()
                .id(504L)
                .projectId(604L)
                .category(Expense.ExpenseCategory.OTHER)
                .expenseType("保证金")
                .status(Expense.ExpenseStatus.APPROVED)
                .expectedReturnDate(LocalDate.of(2026, 5, 6))
                .build();
        BidResultFetchResult result = BidResultFetchResult.builder()
                .projectId(604L)
                .result(BidResultFetchResult.Result.LOST)
                .status(BidResultFetchResult.Status.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(1))
                .build();
        SettingsResponse settings = new SettingsResponse();

        when(expenseRepository.findById(504L)).thenReturn(Optional.of(expense));
        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                604L, BidResultFetchResult.Status.CONFIRMED)).thenReturn(Optional.of(result));
        when(alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN)).thenReturn(List.of());
        when(settingsService.getSettings()).thenReturn(settings);
        when(alertRuleRepository.save(argThat(rule ->
                rule.getThreshold().compareTo(BigDecimal.valueOf(7)) == 0
                        && rule.getType() == AlertRule.AlertType.DEPOSIT_RETURN
        ))).thenAnswer(invocation -> {
            AlertRule rule = invocation.getArgument(0);
            rule.setId(77L);
            return rule;
        });
        when(projectRepository.findById(604L)).thenReturn(Optional.empty());
        when(alertHistoryService.createAlertHistory(any())).thenReturn(AlertHistory.builder().id(2L).build());
        when(expenseRepository.save(expense)).thenReturn(expense);

        sendExpenseReturnReminderAppService.send(504L, "finance-user", "配置缺失兜底");

        verify(accessGuard).assertCanAccessProject(604L);
        verify(alertRuleRepository).save(argThat(rule ->
                rule.getThreshold().compareTo(BigDecimal.valueOf(7)) == 0
                        && rule.getType() == AlertRule.AlertType.DEPOSIT_RETURN
        ));
        verify(expenseRepository).save(expense);
        assertThat(expense.getLastReturnReminderAt()).isNotNull();
    }
}
