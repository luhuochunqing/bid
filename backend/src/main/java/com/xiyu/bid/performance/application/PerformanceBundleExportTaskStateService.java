package com.xiyu.bid.performance.application;

import com.xiyu.bid.common.application.AbstractExportTaskStateService;
import com.xiyu.bid.common.application.ExportTaskCompletion;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity.ExportStatus;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceExportTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

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
}
