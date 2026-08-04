package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.common.application.AbstractExportTaskStateService;
import com.xiyu.bid.common.application.ExportTaskCompletion;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 仓库导出任务状态机服务 — 集中管理 PENDING → PROCESSING → COMPLETED/FAILED 状态转换。
 *
 * <p>D3-2 修复：状态转换逻辑（createTask / markProcessing / complete / fail）已提取到
 * {@link AbstractExportTaskStateService}，本类仅提供仓库实体特定的字段设置实现。
 *
 * @see AbstractExportTaskStateService
 */
@Service
@Slf4j
public class WarehouseExportTaskStateService
        extends AbstractExportTaskStateService<WarehouseExportTaskEntity, WarehouseExportTaskRepository> {

    private static final String DOWNLOAD_URL_PREFIX = "/api/knowledge/warehouses/export/tasks/";

    public WarehouseExportTaskStateService(WarehouseExportTaskRepository repo) {
        super(repo, DOWNLOAD_URL_PREFIX);
    }

    @Override
    protected WarehouseExportTaskEntity newPendingTask(String filterSnapshot, Long operatorId) {
        return WarehouseExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(filterSnapshot)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Override
    protected Long extractId(WarehouseExportTaskEntity task) {
        return task.getId();
    }

    @Override
    protected void applyProcessingStatus(WarehouseExportTaskEntity task) {
        task.setStatus(ExportStatus.PROCESSING);
    }

    @Override
    protected void applyCompletion(WarehouseExportTaskEntity task, ExportTaskCompletion ctx,
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
    protected void applyFailure(WarehouseExportTaskEntity task, String reason, LocalDateTime now) {
        task.setStatus(ExportStatus.FAILED);
        task.setFailureReason(reason);
        task.setCompletedAt(now);
    }
}
