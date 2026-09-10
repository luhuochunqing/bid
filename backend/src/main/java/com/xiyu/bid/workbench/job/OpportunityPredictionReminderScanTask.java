// Input: 无（@Scheduled 触发）
// Output: 每日 09:00 触发商机预测提醒扫描
// Pos: workbench/job - 定时任务，薄调度壳，业务全在 service
package com.xiyu.bid.workbench.job;

import com.xiyu.bid.workbench.service.OpportunityPredictionReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/**
 * 商机预测提醒定时任务：每日 09:00 扫描一次，
 * 凡已到"预测招标时间"且从未提醒过的项目发送商机预测提醒。
 *
 * <p>薄调度壳：异常在壳内兜底，具体逻辑见
 * {@link OpportunityPredictionReminderService#runScan(LocalDate)}。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpportunityPredictionReminderScanTask {

    private final OpportunityPredictionReminderService reminderService;

    @Scheduled(cron = "0 0 9 * * ?")
    public void scan() {
        log.info("[商机预测提醒] scheduled scan start");
        try {
            OpportunityPredictionReminderService.ScanOutcome outcome =
                    reminderService.runScan(LocalDate.now());
            log.info("[商机预测提醒] scheduled scan done: scanned={} notified={} eventRetried={} skipped={}",
                    outcome.scanned(), outcome.notified(), outcome.eventRetried(), outcome.skipped());
        } catch (RuntimeException ex) {
            log.error("[商机预测提醒] scheduled scan failed: {}", ex.getMessage(), ex);
        }
    }
}
