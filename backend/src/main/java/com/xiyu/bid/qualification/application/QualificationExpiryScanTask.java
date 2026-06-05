package com.xiyu.bid.qualification.application;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertRuleService;
import com.xiyu.bid.alerts.service.QualificationExpiryAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class QualificationExpiryScanTask {

    private static final int DEFAULT_THRESHOLD_DAYS = 90;

    private final QualificationExpiryAlertService qualificationExpiryAlertService;
    private final AlertRuleService alertRuleService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void scanExpiringQualifications() {
        log.info("Starting scheduled qualification expiry scan...");
        try {
            int thresholdDays = resolveThresholdDays();
            int alertsCreated = qualificationExpiryAlertService.createAlerts(thresholdDays);
            log.info("Scheduled scan completed. Created {} alerts for qualifications expiring within {} days.",
                    alertsCreated, thresholdDays);
        } catch (RuntimeException exception) {
            log.error("Failed to execute scheduled qualification expiry scan", exception);
        }
    }

    private int resolveThresholdDays() {
        return alertRuleService.getAlertRulesByType(AlertRule.AlertType.QUALIFICATION_EXPIRY)
                .stream()
                .filter(AlertRule::getEnabled)
                .findFirst()
                .map(rule -> rule.getThreshold().intValue())
                .orElseGet(() -> {
                    log.warn("No enabled QUALIFICATION_EXPIRY alert rule found, using default threshold of {} days",
                            DEFAULT_THRESHOLD_DAYS);
                    return DEFAULT_THRESHOLD_DAYS;
                });
    }
}
