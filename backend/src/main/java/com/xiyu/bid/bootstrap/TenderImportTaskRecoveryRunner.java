package com.xiyu.bid.bootstrap;

import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import com.xiyu.bid.tender.service.TenderImportProgressService;
import com.xiyu.bid.tender.service.TenderImportTaskStateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯导入卡死任务恢复 Runner。
 *
 * <p>应用启动时扫描 {@code status='PROCESSING' AND updated_at < now()-30min} 的任务，
 * 标记为 FAILED 并清 Redis 进度缓存。
 *
 * <p><b>背景</b>：异步导入任务在内存中执行，服务重启时 JVM 终止，任务无法恢复。
 * 30 分钟阈值避免误伤正在处理的任务（500 行预计 <60s）。
 *
 * <p>契约来源：{@code specs/031-tender-import-async-perf/contracts/tender-import-task-states.md §卡死任务恢复}
 *
 * <p>放在 bootstrap 包下：bootstrap 包独立于 config 包，专门承载启动期初始化逻辑，
 * 可自由注入 Service/Repository（参见 {@code bootstrap/package-info.java}）。
 * 通过 {@link ApplicationRunner} 被 Spring Boot 自动调用。
 */
@Component
@Profile({"dev", "prod", "mysql"})
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class TenderImportTaskRecoveryRunner implements ApplicationRunner {

    /** 卡死任务阈值（分钟）：updated_at 早于 now()-30min 的 PROCESSING 任务视为卡死 */
    static final int STUCK_THRESHOLD_MINUTES = 30;

    private final TenderImportTaskRepository taskRepository;
    private final TenderImportTaskStateService taskStateService;
    private final TenderImportProgressService progressService;

    @Override
    public void run(ApplicationArguments args) {
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(STUCK_THRESHOLD_MINUTES);
        List<TenderImportTask> stuckTasks = taskRepository
                .findByStatusAndUpdatedAtBefore("PROCESSING", threshold);

        if (stuckTasks.isEmpty()) {
            return;
        }

        log.warn("发现 {} 个卡死的标讯导入任务（PROCESSING 且超过 {} 分钟），开始恢复",
                stuckTasks.size(), STUCK_THRESHOLD_MINUTES);

        List<TenderImportTaskError> errors = List.of(new TenderImportTaskError(
                0, "system", "服务重启导致任务中断", null));

        int recovered = 0;
        for (TenderImportTask task : stuckTasks) {
            String taskId = task.getTaskId();
            try {
                taskStateService.failTaskWithThreeLayerFallback(taskId, errors);
                progressService.clearProgress(taskId);
                recovered++;
                log.warn("卡死任务已标记 FAILED: taskId={} fileName={} updatedAt={}",
                        taskId, task.getFileName(), task.getUpdatedAt());
            } catch (RuntimeException e) {
                // failTaskWithThreeLayerFallback 内部已有三层降级，到这里说明全部失败
                log.error("卡死任务恢复失败（三层降级均失败）: taskId={}", taskId, e);
            }
        }

        log.warn("卡死任务恢复完成: 共 {} 个，成功 {} 个", stuckTasks.size(), recovered);
    }
}
