// Input: score_parse_task 表（PROCESSING 且 updated_at 超阈值）
// Output: 超时任务置 FAILED + timeout_marked=1，清 Redis 进度
// Pos: scoreparse/infrastructure/scheduler — spec 041 US5（FR-020）
// 维护声明: 维护者按项目SOP；参照 WarehouseExpiryScanTask 定时任务范式
package com.xiyu.bid.scoreparse.infrastructure.scheduler;

import com.xiyu.bid.scoreparse.application.ScoreParseProgressService;
import com.xiyu.bid.scoreparse.application.ScoreParseTaskStateService;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评分解析/打分任务超时扫描 Job（spec 041 US5，FR-020）。
 *
 * <p>每 5 分钟扫描 {@code status='PROCESSING' AND updated_at < now()-timeout-minutes}
 * 的任务，标记 FAILED（timeout_marked=1，契约文案"任务超时终止，保留上次成功结果"）
 * 并清 Redis 进度缓存。旧结果不删除（FR-020 保留上次成功结果）。
 *
 * <p>阈值 {@code app.score-parse.timeout-minutes} 默认 30 分钟，可注入。
 */
@Component
@Profile({"dev", "prod", "mysql"})
@Slf4j
public class ScoreParseTimeoutScanJob {

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreParseTaskStateService stateService;
    private final ScoreParseProgressService progressService;
    private final long timeoutMinutes;

    public ScoreParseTimeoutScanJob(
            ScoreParseTaskRepository taskRepository,
            ScoreParseTaskStateService stateService,
            ScoreParseProgressService progressService,
            @Value("${app.score-parse.timeout-minutes:30}") long timeoutMinutes) {
        this.taskRepository = taskRepository;
        this.stateService = stateService;
        this.progressService = progressService;
        this.timeoutMinutes = timeoutMinutes;
    }

    /** 每 5 分钟扫描一次（启动延迟 2 分钟，避开启动高峰） */
    @Scheduled(fixedDelay = 300_000, initialDelay = 120_000)
    public void scanTimedOutTasks() {
        try {
            int marked = processScan(LocalDateTime.now());
            if (marked > 0) {
                log.warn("超时扫描完成: {} 个任务被标记 FAILED（阈值 {} 分钟）", marked, timeoutMinutes);
            }
        } catch (RuntimeException exception) {
            log.error("超时扫描执行失败", exception);
        }
    }

    /** 执行扫描，返回标记数量（时钟可注入便于测试） */
    int processScan(LocalDateTime now) {
        LocalDateTime threshold = now.minusMinutes(timeoutMinutes);
        List<ScoreParseTask> timedOut = taskRepository
                .findByStatusAndUpdatedAtBefore("PROCESSING", threshold);

        int marked = 0;
        for (ScoreParseTask task : timedOut) {
            try {
                stateService.markTimeout(task.getTaskId());
                progressService.clearProgress(task.getTaskId());
                marked++;
                log.warn("任务超时终止: taskId={}, taskType={}, updatedAt={}",
                        task.getTaskId(), task.getTaskType(), task.getUpdatedAt());
            } catch (RuntimeException exception) {
                log.error("超时标记失败，跳过继续: taskId={}", task.getTaskId(), exception);
            }
        }
        return marked;
    }
}
