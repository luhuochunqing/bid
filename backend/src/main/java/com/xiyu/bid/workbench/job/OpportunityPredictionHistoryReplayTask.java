// Input: 应用启动事件、每日 09:00 调度与历史补发安全配置
// Output: 启动 preview 或指定执行日的 preview/execute 调用
// Pos: workbench/job - 一次性商机预测历史补发薄调度壳
// 维护声明: 启动永远不得 execute；错过 runDate 后不得追补执行。
package com.xiyu.bid.workbench.job;

import com.xiyu.bid.config.BizTimezoneProperties;
import com.xiyu.bid.workbench.service.OpportunityPredictionHistoryReplayService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;

/** 默认关闭的一次性任务：启动只预演，指定日期 09:00 才允许执行。 */
@Component
@RequiredArgsConstructor
@Slf4j
public class OpportunityPredictionHistoryReplayTask {

    private final OpportunityPredictionHistoryReplayService replayService;
    private final OpportunityPredictionHistoryReplayProperties properties;
    private final BizTimezoneProperties bizTimezone;

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.LOWEST_PRECEDENCE)
    public void previewOnStartup() {
        if (!properties.isEnabled()) {
            return;
        }
        runSafely(OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW, "startup");
    }

    @Scheduled(cron = "0 0 9 * * ?", zone = "${xiyu.biz.timezone:Asia/Shanghai}")
    public void runScheduled() {
        if (!properties.isEnabled()) {
            return;
        }
        if (!properties.hasValidWindow()) {
            logInvalidConfiguration("scheduled");
            return;
        }
        LocalDate today = bizTimezone.today();
        if (!properties.getRunDate().equals(today)) {
            log.info("[商机预测历史补发] scheduled skip: today={} runDate={}",
                    today, properties.getRunDate());
            return;
        }
        OpportunityPredictionHistoryReplayService.ReplayMode mode = properties.getMode() == null
                ? OpportunityPredictionHistoryReplayService.ReplayMode.PREVIEW
                : properties.getMode();
        runSafely(mode, "scheduled");
    }

    private void runSafely(OpportunityPredictionHistoryReplayService.ReplayMode mode, String trigger) {
        if (!properties.hasValidWindow()) {
            logInvalidConfiguration(trigger);
            return;
        }
        log.info("[商机预测历史补发] {} start mode={} runDate={} cutoff={}",
                trigger, mode, properties.getRunDate(), properties.getCutoff());
        try {
            OpportunityPredictionHistoryReplayService.ReplayOutcome outcome = replayService.run(
                    properties.getRunDate(), properties.getCutoff(), mode);
            log.info("[商机预测历史补发] {} complete mode={} candidates={} eligible={} "
                            + "created={} relations={} alreadyReplayed={} failed={}",
                    trigger, mode, outcome.candidates(), outcome.eligible(), outcome.created(),
                    outcome.targetRecipientRelations(), outcome.alreadyReplayed(), outcome.failed());
        } catch (RuntimeException ex) {
            log.error("[商机预测历史补发] {} failed before completion: {}",
                    trigger, ex.getMessage(), ex);
        }
    }

    private void logInvalidConfiguration(String trigger) {
        log.error("[商机预测历史补发] {} skipped: invalid window runDate={} cutoff={}",
                trigger, properties.getRunDate(), properties.getCutoff());
    }
}
