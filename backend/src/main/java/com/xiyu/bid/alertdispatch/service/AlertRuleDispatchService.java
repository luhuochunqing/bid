package com.xiyu.bid.alertdispatch.service;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertRuleExecutionService;
import com.xiyu.bid.alerts.service.QualificationExpiryNotificationService;
import com.xiyu.bid.performance.application.service.PerformanceExpiryAlertService;
import com.xiyu.bid.performance.application.service.PerformanceAlertConfigAppService;
import com.xiyu.bid.resources.application.service.ScanDepositReturnTrackingAppService;
import com.xiyu.bid.resources.service.CaExpiryScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertRuleDispatchService {

    private final BudgetAlertDispatchService budgetAlertDispatchService;
    private final AlertRuleExecutionService alertRuleExecutionService;
    /** §4.1.3.8 资质到期通知编排（替代旧的 ScanExpiringQualificationsAppService 编排）。 */
    private final QualificationExpiryNotificationService qualificationExpiryNotificationService;
    private final ScanDepositReturnTrackingAppService scanDepositReturnTrackingAppService;
    private final PerformanceExpiryAlertService performanceExpiryAlertService;
    private final PerformanceAlertConfigAppService performanceAlertConfigAppService;
    private final CaExpiryScanService caExpiryScanService;

    public void dispatch(AlertRule rule) {
        switch (rule.getType()) {
            case BUDGET -> budgetAlertDispatchService.dispatch(rule);
            case QUALIFICATION_EXPIRY -> dispatchQualificationExpiry(rule);
            case DEPOSIT_RETURN -> dispatchDepositReturn();
            case PERFORMANCE_EXPIRY -> dispatchPerformanceExpiry();
            case CA_EXPIRY -> dispatchCaExpiry();
            case CA_BORROW_OVERDUE -> dispatchCaBorrowOverdue();
            default -> alertRuleExecutionService.execute(rule);
        }
    }

    private void dispatchQualificationExpiry(AlertRule rule) {
        int thresholdDays = rule.getThreshold() == null ? 0 : rule.getThreshold().intValue();
        log.debug("Dispatching qualification expiry scan with thresholdDays={}", thresholdDays);
        // §4.1.3.8：直接调通知编排，24h 去重保证不重复推送
        qualificationExpiryNotificationService.runScan(thresholdDays, null);
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

    private void dispatchCaExpiry() {
        log.debug("Dispatching CA certificate expiry scan");
        int created = caExpiryScanService.scanCertificateExpiry();
        log.info("CA certificate expiry scan completed: {} alerts created", created);
    }

    private void dispatchCaBorrowOverdue() {
        log.debug("Dispatching CA borrow overdue scan");
        int created = caExpiryScanService.scanBorrowOverdue();
        log.info("CA borrow overdue scan completed: {} alerts created", created);
    }
}
