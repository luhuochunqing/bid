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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link ScanDepositReturnTrackingAppService} 单元测试。
 *
 * <p>P1-4: 使用 AlertRuleProvisioningService.ensureRuleWithThresholdSync 替代直接 AlertRuleRepository 操作。
 * <p>P1-3: 使用 createAndNotifyIfNew 模板方法替代手动 create + dispatch。
 * <p>P1-6: 批量加载 BidResultFetchResult 和 Project（消除 N+1）。
 * <p>P1-11: 副作用仅在 created=true 时执行。
 */
@ExtendWith(MockitoExtension.class)
class ScanDepositReturnTrackingAppServiceTest {

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
        Project project = Project.builder().id(601L).name("测试项目").build();

        when(settingsService.getSettings()).thenReturn(SettingsResponse.builder()
                .systemConfig(SettingsResponse.SystemConfig.builder().depositWarnDays(7).build())
                .build());
        when(alertRuleProvisioningService.ensureRuleWithThresholdSync(
                eq(AlertRule.AlertType.DEPOSIT_RETURN), anyString(), anyInt())).thenReturn(rule);
        when(expenseRepository.findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                "保证金", Expense.ExpenseStatus.RETURNED)).thenReturn(List.of(expense));
        // P1-6: 批量查询而非循环内逐个查
        when(bidResultFetchResultRepository.findByProjectIdsInAndStatus(
                Set.of(601L), BidResultFetchResult.Status.CONFIRMED)).thenReturn(List.of(result));
        when(projectRepository.findAllById(Set.of(601L))).thenReturn(List.of(project));
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), any(AlertRule.class), any()))
                .thenReturn(new AlertHistoryCreateResult(AlertHistory.builder().id(1L).build(), true));

        int reminded = scanDepositReturnTrackingAppService.scan();

        ArgumentCaptor<AlertHistoryCreateRequest> captor =
                ArgumentCaptor.forClass(AlertHistoryCreateRequest.class);
        verify(alertNotificationOrchestrator).createAndNotifyIfNew(captor.capture(), any(AlertRule.class), any());
        verify(expenseRepository).save(expense);
        assertThat(reminded).isEqualTo(1);
        assertThat(expense.getLastReturnReminderAt()).isNotNull();
        // relatedId 格式为 "DepositReturn:{expenseId}"（单冒号，无日期后缀）
        assertThat(captor.getValue().getRelatedId()).isEqualTo("DepositReturn:501");
    }

    @Test
    @DisplayName("自动扫描应把 depositWarnDays 传递给 ensureRuleWithThresholdSync 进行阈值同步")
    void shouldPassDepositWarnDaysToProvisioningService() {
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
        AlertRule rule = AlertRule.builder()
                .id(88L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(9))
                .enabled(true)
                .createdBy("tester")
                .build();

        when(settingsService.getSettings()).thenReturn(SettingsResponse.builder()
                .systemConfig(SettingsResponse.SystemConfig.builder().depositWarnDays(9).build())
                .build());
        // P1-4: 阈值同步逻辑封装在 AlertRuleProvisioningService 中，本服务只负责传递 warnDays
        when(alertRuleProvisioningService.ensureRuleWithThresholdSync(
                AlertRule.AlertType.DEPOSIT_RETURN, "保证金退还提醒", 9)).thenReturn(rule);
        when(expenseRepository.findByExpenseTypeAndExpectedReturnDateIsNotNullAndStatusNotOrderByExpectedReturnDateAsc(
                "保证金", Expense.ExpenseStatus.RETURNED)).thenReturn(List.of(expense));
        when(bidResultFetchResultRepository.findByProjectIdsInAndStatus(
                Set.of(801L), BidResultFetchResult.Status.CONFIRMED)).thenReturn(List.of(result));
        when(projectRepository.findAllById(Set.of(801L))).thenReturn(List.of());
        when(alertNotificationOrchestrator.createAndNotifyIfNew(
                any(AlertHistoryCreateRequest.class), any(AlertRule.class), any()))
                .thenReturn(new AlertHistoryCreateResult(AlertHistory.builder().id(2L).build(), true));

        scanDepositReturnTrackingAppService.scan();

        // 验证 ensureRuleWithThresholdSync 被调用且 warnDays=9（来自系统配置）
        verify(alertRuleProvisioningService).ensureRuleWithThresholdSync(
                AlertRule.AlertType.DEPOSIT_RETURN, "保证金退还提醒", 9);
    }
}
