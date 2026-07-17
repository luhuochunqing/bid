package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 仓库导出任务状态机服务 — 集中管理 PENDING → PROCESSING → COMPLETED/FAILED 状态转换。
 *
 * <p>对标 {@code TenderImportTaskStateService}，提供独立事务的状态更新方法，
 * 确保异步线程中的状态更新不受外层事务影响（避免 @Async + @Transactional 竞态）。
 *
 * <p>状态机：
 * <ul>
 *   <li>{@code createTask}: 创建 PENDING 任务（独立事务，提交后立即可被异步线程查询）</li>
 *   <li>{@code markProcessing}: PENDING → PROCESSING（REQUIRES_NEW）</li>
 *   <li>{@code complete}: PROCESSING → COMPLETED（REQUIRES_NEW，返回 task 供 publish 使用）</li>
 *   <li>{@code fail}: 任何状态 → FAILED（REQUIRES_NEW）</li>
 * </ul>
 *
 * @see com.xiyu.bid.tender.service.TenderImportTaskStateService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportTaskStateService {

    private final WarehouseExportTaskRepository repo;

    /**
     * 创建 PENDING 任务（独立事务）。
     * <p>独立事务设计：调用方无需 {@code @Transactional}，任务记录在方法返回前已提交，
     * 异步线程可立即查询到，避免 {@code @Async} + {@code @Transactional} 竞态导致 findById 返回空。
     */
    @Transactional
    public Long createTask(String filterSnapshot, Long operatorId) {
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(filterSnapshot)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
        return repo.save(task).getId();
    }

    /** PENDING → PROCESSING */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long taskId) {
        repo.findById(taskId).ifPresent(t -> {
            t.setStatus(ExportStatus.PROCESSING);
            repo.save(t);
        });
    }

    /**
     * PROCESSING → COMPLETED。
     *
     * @return 更新后的 task 实体，供调用方执行附加操作（如发布通知）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public WarehouseExportTaskEntity complete(ExportCompletion ctx) {
        LocalDateTime now = LocalDateTime.now();
        WarehouseExportTaskEntity task = repo.findById(ctx.taskId()).orElseThrow();
        task.setStatus(ExportStatus.COMPLETED);
        task.setTotalCount(ctx.totalCount());
        task.setStoredFilePath(ctx.filePath());
        task.setDownloadUrl("/api/knowledge/warehouses/export/tasks/" + ctx.taskId() + "/download");
        task.setExpiresAt(now.plus(ctx.fileTtl()));
        task.setCompletedAt(now);
        task.setResultSummary(ctx.resultSummary());
        return repo.save(task);
    }

    /** 任何状态 → FAILED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String reason) {
        repo.findById(taskId).ifPresent(t -> {
            t.setStatus(ExportStatus.FAILED);
            t.setFailureReason(reason);
            t.setCompletedAt(LocalDateTime.now());
            repo.save(t);
        });
    }

    /** 纯函数：截断字符串到指定长度，null 返回空串。 */
    public static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
