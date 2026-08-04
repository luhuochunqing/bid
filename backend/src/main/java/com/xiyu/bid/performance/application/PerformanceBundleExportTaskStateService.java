package com.xiyu.bid.performance.application;

import com.xiyu.bid.common.application.AbstractExportTaskStateService;
import com.xiyu.bid.common.application.ExportTaskCompletion;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity.ExportStatus;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceExportTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 业绩合订本导出任务状态机服务 — 集中管理 PENDING → PROCESSING → COMPLETED/FAILED 状态转换。
 *
 * <p>D3-2 修复：状态转换逻辑（createTask / markProcessing / complete / fail）已提取到
 * {@link AbstractExportTaskStateService}，本类仅提供业绩实体特定的字段设置实现。
 *
 * @see AbstractExportTaskStateService
 */
@Service
@Slf4j
public class PerformanceBundleExportTaskStateService
        extends AbstractExportTaskStateService<PerformanceExportTaskEntity, PerformanceExportTaskRepository> {

    private static final String DOWNLOAD_URL_PREFIX = "/api/knowledge/performance/bundle-export/tasks/";

    public PerformanceBundleExportTaskStateService(PerformanceExportTaskRepository repo) {
        super(repo, DOWNLOAD_URL_PREFIX);
    }

    @Override
    protected PerformanceExportTaskEntity newPendingTask(String filterSnapshot, Long operatorId) {
        return PerformanceExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(filterSnapshot)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    protected Long extractId(PerformanceExportTaskEntity task) {
        return task.getId();
    }

    @Override
    protected void applyProcessingStatus(PerformanceExportTaskEntity task) {
        task.setStatus(ExportStatus.PROCESSING);
    }

    @Override
    protected void applyCompletion(PerformanceExportTaskEntity task, ExportTaskCompletion ctx,
                                    String downloadUrl, LocalDateTime now) {
        task.setStatus(ExportStatus.COMPLETED);
        task.setTotalCount(ctx.totalCount());
        task.setStoredFilePath(ctx.filePath());
        task.setDownloadUrl(downloadUrl);
        task.setExpiresAt(now.plus(ctx.fileTtl()));
        task.setCompletedAt(now);
        task.setResultSummary(ctx.resultSummary());
    }

    @Override
    protected void applyFailure(PerformanceExportTaskEntity task, String reason, LocalDateTime now) {
        task.setStatus(ExportStatus.FAILED);
        task.setFailureReason(reason);
        task.setCompletedAt(now);
    }

    /**
     * 定时清理过期或失败的导出任务（每天凌晨 3 点）。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanupExpiredTasks() {
        LocalDateTime now = LocalDateTime.now();
        List<PerformanceExportTaskEntity> toDelete = repo.findAll().stream()
                .filter(task -> task.getStatus() == ExportStatus.FAILED
                        || (task.getExpiresAt() != null && task.getExpiresAt().isBefore(now)))
                .toList();
        if (!toDelete.isEmpty()) {
            repo.deleteAll(toDelete);
            log.info("清理过期/失败导出任务完成，共删除 {} 条", toDelete.size());
        }
    }
}
