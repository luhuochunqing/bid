package com.xiyu.bid.qualification.application;

import com.xiyu.bid.alerts.entity.AlertRule;
import com.xiyu.bid.alerts.service.AlertRuleService;
import com.xiyu.bid.alerts.service.QualificationExpiryAlertService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时扫描资质到期并生成提醒。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QualificationExpiryScanTask {

    /** 默认提醒阈值天数。 */
    private static final int DEFAULT_THRESHOLD_DAYS = 90;

    /** 资质到期提醒服务。 */
    private final QualificationExpiryAlertService
            qualificationExpiryAlertService;

    /** 告警规则服务。 */
    private final AlertRuleService alertRuleService;

    /**
     * 每天凌晨执行一次资质到期扫描。
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void scanExpiringQualifications() {
        log.info("Starting scheduled qualification expiry scan...");
        try {
            final int thresholdDays = resolveThresholdDays();
            final int alertsCreated =
                    qualificationExpiryAlertService.createAlerts(
                            thresholdDays);
            log.info(
                    "Scheduled scan completed. Created {} alerts "
                            + "for qualifications expiring within {} days.",
                    alertsCreated,
                    thresholdDays
            );
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to execute scheduled qualification expiry scan",
                    exception
            );
        }
    }

    private int resolveThresholdDays() {
        return alertRuleService
                .getAlertRulesByType(AlertRule.AlertType.QUALIFICATION_EXPIRY)
                .stream()
                .filter(AlertRule::getEnabled)
                .findFirst()
                .map(rule -> rule.getThreshold().intValue())
                .orElseGet(() -> {
                    log.warn(
                            "No enabled QUALIFICATION_EXPIRY alert rule found, "
                                    + "using default threshold of {} days",
                            DEFAULT_THRESHOLD_DAYS
                    );
                    return DEFAULT_THRESHOLD_DAYS;
                });
    }
}
