package com.xiyu.bid.common.application;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 导出任务状态机服务抽象基类（CO-602 设计评估 D3-2 修复）。
 *
 * <p>抽取 {@code PerformanceBundleExportTaskStateService} 与
 * {@code WarehouseExportTaskStateService} 中逐字重复的状态转换逻辑：
 * <ul>
 *   <li>{@code createTask}: 创建 PENDING 任务（独立事务，提交后立即可被异步线程查询）</li>
 *   <li>{@code markProcessing}: PENDING → PROCESSING（REQUIRES_NEW）</li>
 *   <li>{@code complete}: PROCESSING → COMPLETED（REQUIRES_NEW，返回 task 供调用方附加操作）</li>
 *   <li>{@code fail}: 任何状态 → FAILED（REQUIRES_NEW）</li>
 * </ul>
 *
 * <p>独立事务设计：调用方无需 {@code @Transactional}，任务记录在方法返回前已提交，
 * 异步线程可立即查询到，避免 {@code @Async} + {@code @Transactional} 竞态导致 findById 返回空。
 *
 * <p>子类需实现：
 * <ul>
 *   <li>{@link #newPendingTask(String, Long)} — 创建 PENDING 实体</li>
 *   <li>{@link #extractId(Object)} — 从已保存实体提取 ID</li>
 *   <li>{@link #applyProcessingStatus(Object)} — 设置 PROCESSING 状态字段</li>
 *   <li>{@link #applyCompletion(Object, ExportTaskCompletion, String, LocalDateTime)} — 设置 COMPLETED 字段</li>
 *   <li>{@link #applyFailure(Object, String, LocalDateTime)} — 设置 FAILED 字段</li>
 * </ul>
 *
 * @param <E> 任务实体类型
 * @param <R> 任务仓库类型
 */
public abstract class AbstractExportTaskStateService<E, R extends JpaRepository<E, Long>> {

    protected final R repo;
    private final String downloadUrlPrefix;

    /**
     * @param repo 任务仓库
     * @param downloadUrlPrefix 下载 URL 前缀（如 {@code "/api/knowledge/performance/bundle-export/tasks/"}）
     */
    protected AbstractExportTaskStateService(R repo, String downloadUrlPrefix) {
        this.repo = repo;
        this.downloadUrlPrefix = downloadUrlPrefix;
    }

    /**
     * 创建 PENDING 任务（独立事务）。
     */
    @Transactional
    public Long createTask(String filterSnapshot, Long operatorId) {
        E task = newPendingTask(filterSnapshot, operatorId);
        E saved = repo.save(task);
        return extractId(saved);
    }

    /** PENDING → PROCESSING */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long taskId) {
        repo.findById(taskId).ifPresent(task -> {
            applyProcessingStatus(task);
            repo.save(task);
        });
    }

    /**
     * PROCESSING → COMPLETED。
     *
     * @return 更新后的 task 实体，供调用方执行附加操作（如发布通知）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public E complete(ExportTaskCompletion ctx) {
        LocalDateTime now = LocalDateTime.now();
        E task = repo.findById(ctx.taskId()).orElseThrow();
        String downloadUrl = downloadUrlPrefix + ctx.taskId() + "/download";
        applyCompletion(task, ctx, downloadUrl, now);
        return repo.save(task);
    }

    /** 任何状态 → FAILED */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long taskId, String reason) {
        repo.findById(taskId).ifPresent(task -> {
            applyFailure(task, reason, LocalDateTime.now());
            repo.save(task);
        });
    }

    // ========== 子类实现的抽象方法 ==========

    /** 创建新的 PENDING 状态实体。 */
    protected abstract E newPendingTask(String filterSnapshot, Long operatorId);

    /** 从已保存实体提取主键 ID。 */
    protected abstract Long extractId(E task);

    /** 设置实体为 PROCESSING 状态。 */
    protected abstract void applyProcessingStatus(E task);

    /** 设置实体的 COMPLETED 字段（状态、总数、文件路径、下载 URL、过期时间、完成时间、结果摘要）。 */
    protected abstract void applyCompletion(E task, ExportTaskCompletion ctx, String downloadUrl, LocalDateTime now);

    /** 设置实体的 FAILED 字段（状态、失败原因、完成时间）。 */
    protected abstract void applyFailure(E task, String reason, LocalDateTime now);
}
