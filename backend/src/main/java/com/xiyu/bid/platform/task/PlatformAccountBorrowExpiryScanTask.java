// Input: PlatformAccountBorrowExpiryScanService
// Output: CO-523 账户管理 — 平台账号借用到期/逾期/待审批定时扫描入口
// Pos: Task/定时任务外壳 — 仅负责调度与异常兜底
package com.xiyu.bid.platform.task;

import com.xiyu.bid.platform.service.PlatformAccountBorrowExpiryScanService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
@Slf4j
public class PlatformAccountBorrowExpiryScanTask {

    private final PlatformAccountBorrowExpiryScanService scanService;

    /**
     * 每日 09:00 扫描平台账号借用的待审批、即将到期、已逾期场景。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void scan() {
        log.info("[PlatformAccountBorrowExpiryScanTask] Starting scheduled scan...");
        try {
            int sent = scanService.scan(LocalDateTime.now());
            log.info("[PlatformAccountBorrowExpiryScanTask] Scan completed, sent {} reminders.", sent);
        } catch (RuntimeException ex) {
            log.error("[PlatformAccountBorrowExpiryScanTask] Scheduled scan failed", ex);
        }
    }
}
