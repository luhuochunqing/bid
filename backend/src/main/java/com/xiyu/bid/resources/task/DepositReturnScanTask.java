package com.xiyu.bid.resources.task;

import com.xiyu.bid.resources.application.service.ScanDepositReturnTrackingAppService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 保证金退还跟踪定时扫描任务。
 * <p>
 * 纯核心在哪里：DepositReturnReminderPolicy 的提醒决策逻辑。
 * 副作用在哪里：通过 AlertHistoryService 写入告警历史，并通过 ensureAlertRule 自举创建 DEPOSIT_RETURN 规则。
 * 每天 09:10 执行一次保证金退还提醒扫描。</p>
 * <p>
 * 设计说明：DEPOSIT_RETURN 规则存在"触发链死锁"——alert_rules 表无规则时 AlertSchedulerService
 * 不会调度 dispatchDepositReturn()，scan() 永远不被调用，ensureAlertRule 永远不执行，规则永远不被创建。
 * 本独立 Task 直接调 scan() 打破死锁：首次执行后规则被自举创建，之后 AlertSchedulerService 也能调度。</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DepositReturnScanTask {

    private final ScanDepositReturnTrackingAppService scanDepositReturnTrackingAppService;

    /**
     * 每天 09:10 执行保证金退还提醒扫描。
     * <p>scan() 内部通过 lastReturnReminderAt 去重，保证同一条记录不会在短时间内重复提醒。</p>
     */
    @Scheduled(cron = "0 10 9 * * ?")
    public void scanDepositReturn() {
        log.info("Starting scheduled deposit return tracking scan at 09:10...");
        try {
            int reminded = scanDepositReturnTrackingAppService.scan();
            log.info("Deposit return tracking scan completed. Created {} reminders.", reminded);
        } catch (RuntimeException e) {
            log.error("Failed to execute deposit return tracking scan", e);
        }
    }
}
