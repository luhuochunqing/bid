package com.xiyu.bid.resources.task;

import com.xiyu.bid.resources.service.CaExpiryScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CA 证书到期及借用逾期定时扫描任务。
 * <p>
 * 纯核心在哪里：CaExpiryScanService 的扫描逻辑。
 * 副作用在哪里：通过 AlertHistoryService 写入告警历史。
 * 每天 09:00 执行一次 CA 证书到期扫描和借用逾期扫描。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CaExpiryScanTask {

    private final CaExpiryScanService caExpiryScanService;

    /**
     * 每天 09:00 执行 CA 证书到期提醒扫描。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scanCertificateExpiry() {
        log.info("Starting scheduled CA certificate expiry scan at 09:00...");
        try {
            int alertsCreated = caExpiryScanService.scanCertificateExpiry();
            log.info("CA certificate expiry scan completed. Created {} alerts.", alertsCreated);
        } catch (RuntimeException e) {
            log.error("Failed to execute CA certificate expiry scan", e);
        }
    }

    /**
     * 每天 09:00 执行 CA 借用逾期提醒扫描。
     */
    @Scheduled(cron = "0 5 9 * * ?")
    public void scanBorrowOverdue() {
        log.info("Starting scheduled CA borrow overdue scan at 09:05...");
        try {
            int alertsCreated = caExpiryScanService.scanBorrowOverdue();
            log.info("CA borrow overdue scan completed. Created {} alerts.", alertsCreated);
        } catch (RuntimeException e) {
            log.error("Failed to execute CA borrow overdue scan", e);
        }
    }
}
