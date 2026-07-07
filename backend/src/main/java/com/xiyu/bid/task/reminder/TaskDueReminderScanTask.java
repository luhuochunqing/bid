// Input: TaskDueReminderService
// Output: 每日 09:00 触发即将到期 + 逾期扫描
// Pos: task/reminder - @Scheduled 调度入口，无业务逻辑
// 维护声明:
//   - 仅做调度入口，业务委托给 TaskDueReminderService；
//   - cron = "0 0 9 * * ?"（每日 09:00）；
//   - alertDays 默认 3 天，后续可从 AlertConfig 扩展。
package com.xiyu.bid.task.reminder;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * CO-533 任务到期/逾期提醒定时扫描任务。
 *
 * <p>对齐蓝图 §1.1/§1.2 扫描频率：每日 09:00。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskDueReminderScanTask {

    private final TaskDueReminderService reminderService;

    /**
     * 每日 09:00 触发即将到期扫描（默认提前 3 天）。
     */
    @Scheduled(cron = "0 0 9 * * ?")
    public void runDueSoonScan() {
        log.info("[CO-533] 定时任务触发：即将到期扫描");
        TaskDueReminderService.ScanOutcome outcome =
                reminderService.runDueSoonScan(TaskDueReminderService.DEFAULT_ALERT_DAYS, null);
        log.info("[CO-533] 即将到期扫描完成: {}", outcome);
    }

    /**
     * 每日 09:05 触发逾期扫描（错开 5 分钟避免同时打 DB）。
     */
    @Scheduled(cron = "0 5 9 * * ?")
    public void runOverdueScan() {
        log.info("[CO-533] 定时任务触发：逾期扫描");
        TaskDueReminderService.ScanOutcome outcome = reminderService.runOverdueScan(null);
        log.info("[CO-533] 逾期扫描完成: {}", outcome);
    }
}
