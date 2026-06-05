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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ScanDepositReturnTrackingAppServiceTest {

    @Mock
    private ExpenseRepository expenseRepository;
    @Mock
    private BidResultFetchResultRepository bidResultFetchResultRepository;
    @Mock
    private AlertRuleRepository alertRuleRepository;
    @Mock
    private AlertHistoryService alertHistoryService;
    @Mock
    private SettingsService settingsService;
    @Mock
    private ProjectRepository projectRepository;

    @InjectMocks
    private ScanDepositReturnTrackingAppService scanDepositReturnTrackingAppService;

    @Test
    @DisplayName("自动扫描应消费 depositWarnDays 并记录提醒时间")
    void shouldUseDepositWarnDaysAndPersistReminderTime() {
        Expense expense = Expense.builder()
                .id(501L)
                .projectId(601L)
                .expenseType("保证金")
                .status(Expense.ExpenseStatus.APPROVED)
                .expectedReturnDate(LocalDate.now().plusDays(3))
                .build();
        BidResultFetchResult result = BidResultFetchResult.builder()
                .projectId(601L)
                .result(BidResultFetchResult.Result.LOST)
                .status(BidResultFetchResult.Status.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(1))
                .build();
        AlertRule rule = AlertRule.builder()
                .id(41L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("tester")
                .build();

        when(settingsService.getSettings()).thenReturn(SettingsResponse.builder()
                .systemConfig(SettingsResponse.SystemConfig.builder().depositWarnDays(7).build())
                .build());
        when(alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN)).thenReturn(List.of(rule));
        when(expenseRepository.findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                "保证金", Expense.ExpenseStatus.RETURNED)).thenReturn(List.of(expense));
        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                601L, BidResultFetchResult.Status.CONFIRMED)).thenReturn(Optional.of(result));
        when(alertHistoryService.createAlertHistory(any())).thenReturn(AlertHistory.builder().id(1L).build());

        int reminded = scanDepositReturnTrackingAppService.scan();

        ArgumentCaptor<com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest> captor =
                ArgumentCaptor.forClass(com.xiyu.bid.alerts.dto.AlertHistoryCreateRequest.class);
        verify(alertHistoryService).createAlertHistory(captor.capture());
        verify(expenseRepository).save(expense);
        assertThat(reminded).isEqualTo(1);
        assertThat(expense.getLastReturnReminderAt()).isNotNull();
        assertThat(captor.getValue().getRelatedId()).contains("DepositReturn:501:");
    }

    @Test
    @DisplayName("自动扫描应把保证金规则阈值同步为系统设置值")
    void shouldSyncDepositReturnRuleThresholdToSettings() {
        Expense expense = Expense.builder()
                .id(701L)
                .projectId(801L)
                .expenseType("保证金")
                .status(Expense.ExpenseStatus.APPROVED)
                .expectedReturnDate(LocalDate.now().plusDays(2))
                .build();
        BidResultFetchResult result = BidResultFetchResult.builder()
                .projectId(801L)
                .result(BidResultFetchResult.Result.LOST)
                .status(BidResultFetchResult.Status.CONFIRMED)
                .confirmedAt(LocalDateTime.now().minusDays(1))
                .build();
        AlertRule staleRule = AlertRule.builder()
                .id(88L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.GREATER_THAN)
                .threshold(BigDecimal.valueOf(3))
                .enabled(false)
                .createdBy("tester")
                .build();

        when(settingsService.getSettings()).thenReturn(SettingsResponse.builder()
                .systemConfig(SettingsResponse.SystemConfig.builder().depositWarnDays(9).build())
                .build());
        when(alertRuleRepository.findByType(AlertRule.AlertType.DEPOSIT_RETURN)).thenReturn(List.of(staleRule));
        when(alertRuleRepository.save(argThat(rule ->
                rule.getId().equals(88L)
                        && rule.getThreshold().compareTo(BigDecimal.valueOf(9)) == 0
                        && rule.getCondition() == AlertRule.ConditionType.LESS_THAN
                        && rule.getEnabled()
        ))).thenAnswer(invocation -> invocation.getArgument(0));
        when(expenseRepository.findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                "保证金", Expense.ExpenseStatus.RETURNED)).thenReturn(List.of(expense));
        when(bidResultFetchResultRepository.findFirstByProjectIdAndStatusOrderByConfirmedAtDescFetchTimeDesc(
                801L, BidResultFetchResult.Status.CONFIRMED)).thenReturn(Optional.of(result));
        when(alertHistoryService.createAlertHistory(any())).thenReturn(AlertHistory.builder().id(2L).build());

        scanDepositReturnTrackingAppService.scan();

        verify(alertRuleRepository).save(argThat(rule ->
                rule.getId().equals(88L)
                        && rule.getThreshold().compareTo(BigDecimal.valueOf(9)) == 0
                        && rule.getCondition() == AlertRule.ConditionType.LESS_THAN
                        && rule.getEnabled()
        ));
    }
}
