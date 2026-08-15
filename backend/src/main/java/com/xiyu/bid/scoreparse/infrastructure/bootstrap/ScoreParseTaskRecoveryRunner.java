// Input: score_parse_task 表（PROCESSING 且 updated_at 超阈值的卡死任务）
// Output: 卡死任务 failTask 三层降级 + 清 Redis 进度
// Pos: scoreparse/infrastructure/bootstrap — spec 041 US5（FR-020）
// 维护声明: 维护者按项目SOP；参照 TenderImportTaskRecoveryRunner（spec 031）范式
package com.xiyu.bid.scoreparse.infrastructure.bootstrap;

import com.xiyu.bid.scoreparse.application.ScoreParseProgressService;
import com.xiyu.bid.scoreparse.application.ScoreParseTaskStateService;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 评分解析/打分卡死任务恢复 Runner（spec 041 US5）。
 *
 * <p>应用启动时扫描 {@code status='PROCESSING' AND updated_at < now()-30min}
 * 的任务，复用 {@link ScoreParseTaskStateService#failTask} 三层降级标记 FAILED
 * 并清 Redis 进度缓存。
 *
 * <p><b>背景</b>：解析/打分任务在 @Async 线程内存执行，服务重启时 JVM 终止
 * 无法恢复；30 分钟阈值避免误伤启动后刚触发的新任务（与 spec 031 恢复 Runner
 * 同阈值；阈值内卡死任务由 {@code ScoreParseTimeoutScanJob} 定时兜底）。
 */
@Component
@Profile({"dev", "prod", "mysql"})
@Slf4j
@Order(30)
public class ScoreParseTaskRecoveryRunner implements ApplicationRunner {

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreParseTaskStateService stateService;
    private final ScoreParseProgressService progressService;
    private final long stuckThresholdMinutes;

    public ScoreParseTaskRecoveryRunner(
            ScoreParseTaskRepository taskRepository,
            ScoreParseTaskStateService stateService,
            ScoreParseProgressService progressService,
            @Value("${app.score-parse.timeout-minutes:30}") long stuckThresholdMinutes) {
        this.taskRepository = taskRepository;
        this.stateService = stateService;
        this.progressService = progressService;
        this.stuckThresholdMinutes = stuckThresholdMinutes;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover(LocalDateTime.now());
    }

    /** 执行恢复，返回恢复数量（时钟可注入便于测试） */
    int recover(LocalDateTime now) {
        LocalDateTime threshold = now.minusMinutes(stuckThresholdMinutes);
        List<ScoreParseTask> stuckTasks = taskRepository
                .findByStatusAndUpdatedAtBefore("PROCESSING", threshold);

        if (stuckTasks.isEmpty()) {
            return 0;
        }

        log.warn("发现 {} 个卡死的评分任务（PROCESSING 且超过 {} 分钟），开始恢复",
                stuckTasks.size(), stuckThresholdMinutes);

        int recovered = 0;
        for (ScoreParseTask task : stuckTasks) {
            String taskId = task.getTaskId();
            try {
                stateService.failTask(taskId, "服务重启导致任务中断，请重新触发");
                progressService.clearProgress(taskId);
                recovered++;
                log.warn("卡死任务已标记 FAILED: taskId={} taskType={} updatedAt={}",
                        taskId, task.getTaskType(), task.getUpdatedAt());
            } catch (RuntimeException exception) {
                // failTask 内部已有三层降级，到这里说明全部失败
                log.error("卡死任务恢复失败（三层降级均失败）: taskId={}", taskId, exception);
            }
        }
        log.warn("卡死任务恢复完成: 共 {} 个，成功 {} 个", stuckTasks.size(), recovered);
        return recovered;
    }
}
