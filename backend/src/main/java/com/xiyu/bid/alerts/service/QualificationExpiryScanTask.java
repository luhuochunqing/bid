// Input: AlertConfig (alertDays/enabled) + QualificationExpiryNotificationService
// Output: §4.1.3.8 蓝图每日 09:00 定时扫描
// Pos: 业务层/调度任务 - 资质到期提醒定时器
// 维护声明:
//   - 阈值改用 AlertConfigAppService 提供的全局配置（前端 UI 改的就是这里）；
//   - 渠道切换为 QualificationExpiryNotificationService（站内信 + 企微）；
//   - 严格按蓝图要求 09:00 触发，时区跟随 JVM 默认。
package com.xiyu.bid.alerts.service;

import com.xiyu.bid.businessqualification.application.service.AlertConfigAppService;
import com.xiyu.bid.businessqualification.domain.model.AlertConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * §4.1.3.8 资质到期提醒定时任务。
 * <p>
 * 蓝图要求每日 09:00 触发；本任务读取 AlertConfig 阈值，
 * 调 {@link QualificationExpiryNotificationService#runScan} 完成扫描。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QualificationExpiryScanTask {

    private final AlertConfigAppService alertConfigAppService;
    private final QualificationExpiryNotificationService notificationService;

    /**
     * 每日 09:00 触发一次（cron 6 字段去掉秒即可：0 0 9 * * ?）。
     * <p>
     * 注意：Spring @Scheduled cron 是 6 字段（含秒），故必须带秒位 0。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scanExpiringQualifications() {
        log.info("[§4.1.3.8] Starting scheduled qualification expiry scan at 09:00...");
        try {
            AlertConfig config = alertConfigAppService.getConfig();
            if (!config.enabled()) {
                log.info("[§4.1.3.8] alert config disabled, skip scheduled scan");
                return;
            }
            QualificationExpiryNotificationService.ScanOutcome outcome =
                    notificationService.runScan(config.alertDays(), null);
            log.info(
                    "[§4.1.3.8] Scheduled scan completed. scanned={} notified={} skipped={} (alertDays={})",
                    outcome.scanned(), outcome.notified(), outcome.skipped(), config.alertDays()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[§4.1.3.8] Failed to execute scheduled qualification expiry scan",
                    exception
            );
        }
    }
}
