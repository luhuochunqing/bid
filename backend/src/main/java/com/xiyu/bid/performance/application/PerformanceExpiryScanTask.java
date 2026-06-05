package com.xiyu.bid.performance.application;

import com.xiyu.bid.performance.application.service.PerformanceExpiryAlertService;
import com.xiyu.bid.performance.application.service.PerformanceAlertConfigAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceExpiryScanTask {

    private final PerformanceExpiryAlertService performanceExpiryAlertService;
    private final PerformanceAlertConfigAppService configService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void scanExpiringPerformances() {
        log.info("[PerformanceExpiryScanTask] Starting scheduled performance expiry scan...");
        try {
            var config = configService.getConfig();
            if (!config.enabled()) {
                log.info("[PerformanceExpiryScanTask] Performance expiry alerts are disabled, skipping.");
                return;
            }
            int alertsCreated = performanceExpiryAlertService.createAlerts(config);
            log.info("[PerformanceExpiryScanTask] Scan completed. Created {} alerts.", alertsCreated);
        } catch (Exception e) {
            log.error("[PerformanceExpiryScanTask] Failed to execute scheduled performance expiry scan: {}",
                    e.getMessage(), e);
        }
    }
}
