package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertRuleExecutionService;
import com.xiyu.bid.businessqualification.application.service.ScanExpiringQualificationsAppService;
import com.xiyu.bid.performance.application.service.PerformanceExpiryAlertService;
import com.xiyu.bid.performance.application.service.PerformanceAlertConfigAppService;
import com.xiyu.bid.resources.application.service.ScanDepositReturnTrackingAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleDispatchService {

    private final BudgetAlertDispatchService budgetAlertDispatchService;
    private final AlertRuleExecutionService alertRuleExecutionService;
    private final ScanExpiringQualificationsAppService scanExpiringQualificationsAppService;
    private final ScanDepositReturnTrackingAppService scanDepositReturnTrackingAppService;
    private final PerformanceExpiryAlertService performanceExpiryAlertService;
    private final PerformanceAlertConfigAppService performanceAlertConfigAppService;

    public void dispatch(AlertRule rule) {
        switch (rule.getType()) {
            case BUDGET -> budgetAlertDispatchService.dispatch(rule);
            case QUALIFICATION_EXPIRY -> dispatchQualificationExpiry(rule);
            case DEPOSIT_RETURN -> dispatchDepositReturn();
            case PERFORMANCE_EXPIRY -> dispatchPerformanceExpiry();
            default -> alertRuleExecutionService.execute(rule);
        }
    }

    private void dispatchQualificationExpiry(AlertRule rule) {
        int thresholdDays = rule.getThreshold() == null ? 0 : rule.getThreshold().intValue();
        log.debug("Dispatching qualification expiry scan with thresholdDays={}", thresholdDays);
        scanExpiringQualificationsAppService.scan(thresholdDays);
    }

    private void dispatchDepositReturn() {
        log.debug("Dispatching deposit return scan");
        scanDepositReturnTrackingAppService.scan();
    }

    private void dispatchPerformanceExpiry() {
        log.debug("Dispatching performance expiry scan");
        var config = performanceAlertConfigAppService.getConfig();
        if (!config.enabled()) {
            log.debug("Performance expiry alerts are disabled, skipping.");
            return;
        }
        performanceExpiryAlertService.createAlerts(config);
    }
}
