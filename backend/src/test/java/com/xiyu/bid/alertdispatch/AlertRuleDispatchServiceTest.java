package com.xiyu.bid.alertdispatch;

import com.xiyu.bid.alertdispatch.service.AlertRuleDispatchService;
import com.xiyu.bid.alertdispatch.service.BudgetAlertDispatchService;
import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertRuleExecutionService;
import com.xiyu.bid.businessqualification.application.service.ScanExpiringQualificationsAppService;
import com.xiyu.bid.performance.application.service.PerformanceAlertConfigAppService;
import com.xiyu.bid.performance.application.service.PerformanceExpiryAlertService;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.resources.application.service.ScanDepositReturnTrackingAppService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertRuleDispatchServiceTest {

    @Mock
    private BudgetAlertDispatchService budgetAlertDispatchService;
    @Mock
    private AlertRuleExecutionService alertRuleExecutionService;
    @Mock
    private ScanExpiringQualificationsAppService scanExpiringQualificationsAppService;
    @Mock
    private ScanDepositReturnTrackingAppService scanDepositReturnTrackingAppService;
    @Mock
    private PerformanceExpiryAlertService performanceExpiryAlertService;
    @Mock
    private PerformanceAlertConfigAppService performanceAlertConfigAppService;

    @InjectMocks
    private AlertRuleDispatchService alertRuleDispatchService;

    @Test
    @DisplayName("预算规则应委托预算编排器")
    void shouldDelegateBudgetRuleToBudgetDispatcher() {
        AlertRule rule = AlertRule.builder()
                .id(10L)
                .name("预算预警")
                .type(AlertRule.AlertType.BUDGET)
                .condition(AlertRule.ConditionType.GREATER_THAN)
                .threshold(BigDecimal.valueOf(70))
                .enabled(true)
                .createdBy("tester")
                .build();

        alertRuleDispatchService.dispatch(rule);

        verify(budgetAlertDispatchService).dispatch(rule);
    }

    @Test
    @DisplayName("标准规则应委托给 alerts 核心执行器")
    void shouldDelegateCoreRuleToAlertsExecutionService() {
        AlertRule rule = AlertRule.builder()
                .id(11L)
                .name("截止提醒")
                .type(AlertRule.AlertType.DEADLINE)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(3))
                .enabled(true)
                .createdBy("tester")
                .build();

        alertRuleDispatchService.dispatch(rule);

        verify(alertRuleExecutionService).execute(rule);
    }

    @Test
    @DisplayName("资质到期规则应委托资质扫描应用服务")
    void shouldDelegateQualificationExpiryRuleToScanner() {
        AlertRule rule = AlertRule.builder()
                .id(21L)
                .name("资质到期提醒")
                .type(AlertRule.AlertType.QUALIFICATION_EXPIRY)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(15))
                .enabled(true)
                .createdBy("tester")
                .build();

        alertRuleDispatchService.dispatch(rule);

        verify(scanExpiringQualificationsAppService).scan(15);
    }

    @Test
    @DisplayName("保证金规则应委托保证金扫描应用服务")
    void shouldDelegateDepositReturnRuleToScanner() {
        AlertRule rule = AlertRule.builder()
                .id(31L)
                .name("保证金退还提醒")
                .type(AlertRule.AlertType.DEPOSIT_RETURN)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(7))
                .enabled(true)
                .createdBy("tester")
                .build();

        when(scanDepositReturnTrackingAppService.scan()).thenReturn(2);

        alertRuleDispatchService.dispatch(rule);

        verify(scanDepositReturnTrackingAppService).scan();
    }

    @Test
    @DisplayName("业绩到期规则应委托业绩到期提醒服务")
    void shouldDelegatePerformanceExpiryRuleToPerformanceExpiryService() {
        AlertRule rule = AlertRule.builder()
                .id(41L)
                .name("业绩合同到期提醒")
                .type(AlertRule.AlertType.PERFORMANCE_EXPIRY)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(180))
                .enabled(true)
                .createdBy("system")
                .build();

        PerformanceAlertConfig config = new PerformanceAlertConfig(1L, 180, 90, true);
        when(performanceAlertConfigAppService.getConfig()).thenReturn(config);
        when(performanceExpiryAlertService.createAlerts(config)).thenReturn(3);

        alertRuleDispatchService.dispatch(rule);

        verify(performanceAlertConfigAppService).getConfig();
        verify(performanceExpiryAlertService).createAlerts(config);
    }

    @Test
    @DisplayName("业绩到期规则禁用时应跳过扫描")
    void shouldSkipPerformanceExpiryScanWhenDisabled() {
        AlertRule rule = AlertRule.builder()
                .id(42L)
                .name("业绩合同到期提醒")
                .type(AlertRule.AlertType.PERFORMANCE_EXPIRY)
                .condition(AlertRule.ConditionType.LESS_THAN)
                .threshold(BigDecimal.valueOf(180))
                .enabled(false)
                .createdBy("system")
                .build();

        PerformanceAlertConfig config = new PerformanceAlertConfig(1L, 180, 90, false);
        when(performanceAlertConfigAppService.getConfig()).thenReturn(config);

        alertRuleDispatchService.dispatch(rule);

        verify(performanceAlertConfigAppService).getConfig();
    }
}
