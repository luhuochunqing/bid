// Input: QualificationReviewNotificationService
// Output: CO-532 每日 09:00 定时扫描证书审核提醒
// Pos: 业务层/调度任务 - 资质证书审核提醒定时器
// 维护声明:
//   - 提前天数固定 90 天（CO-532 需求），不读 AlertConfig；
//   - 严格按蓝图要求 09:00 触发，时区跟随 JVM 默认。
package com.xiyu.bid.alerts.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CO-532 资质证书审核提醒定时任务。
 * <p>
 * 蓝图要求每日 09:00 触发；本任务调用
 * {@link QualificationReviewNotificationService#runScan} 完成扫描。
 * 提前天数固定 90 天（CO-532 需求明确，不读 AlertConfig）。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class QualificationReviewScanTask {

    private final QualificationReviewNotificationService notificationService;

    /**
     * 每日 09:00 触发一次（cron 6 字段：0 0 9 * * ?）。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scanQualificationReviewReminders() {
        log.info("[CO-532] Starting scheduled qualification review scan at 09:00...");
        try {
            QualificationReviewNotificationService.ScanOutcome outcome =
                    notificationService.runScan(null);
            log.info(
                    "[CO-532] Scheduled scan completed. scanned={} notified={} skipped={}",
                    outcome.scanned(), outcome.notified(), outcome.skipped()
            );
        } catch (RuntimeException exception) {
            log.error(
                    "[CO-532] Failed to execute scheduled qualification review scan",
                    exception
            );
        }
    }
}
