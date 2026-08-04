package com.xiyu.bid.performance.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.common.util.PathUtils;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.config.PerformanceBundleExportProperties;
import com.xiyu.bid.performance.domain.AttachmentFilter;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity.ExportStatus;
import com.xiyu.bid.performance.infrastructure.persistence.repository.PerformanceExportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 业绩合订本导出应用服务 — 只做编排，不含业务规则。
 *
 * <p>职责：
 * <ol>
 *   <li>委托 {@link PerformanceBundleExportTaskStateService#createTask} 创建 PENDING 任务</li>
 *   <li>委托 {@link PerformanceBundleExportAsyncExecutor} 执行异步导出（@Async 代理生效）</li>
 *   <li>提供任务查询和文件下载能力</li>
 * </ol>
 *
 * <p>事务边界设计：本类不标注 @Transactional。createTask 以独立事务提交后立即返回 taskId，
 * 避免原 @Async + @Transactional 竞态。
 *
 * <p>@Async 方法已提取到 {@link PerformanceBundleExportAsyncExecutor}，
 * 避免 Spring AOP self-invocation 导致 @Async 注解失效。
 *
 * <p><b>路径遍历防护</b>：{@link #getExportFile} 校验 {@code stored_file_path} 必须落在
 * {@code exportRoot} 子树内，避免数据库被污染时读取任意系统文件（defense-in-depth，
 * 与 {@code PerformanceAttachmentStorageAppService#resolveLocalPath} 保持一致）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PerformanceBundleExportAppService {

    private final PerformanceExportTaskRepository exportTaskRepo;
    private final PerformanceBundleExportAsyncExecutor asyncExecutor;
    private final PerformanceBundleExportTaskStateService stateService;
    private final ObjectMapper objectMapper;
    private final PerformanceBundleExportProperties properties;

    /**
     * 按 filter 模式创建合订本导出任务，触发异步执行。
     */
    public ExportTaskResult export(PerformanceSearchCriteria criteria,
                                    Set<String> attachmentTypes,
                                    Long operatorId) {
        AttachmentFilter.validateTypes(attachmentTypes);
        String filterSnapshot = serializeCriteria(criteria, attachmentTypes);
        String filterSummary = PerformanceBundleExportNotificationPublisher.buildFilterSummary(
                criteria != null ? criteria.keyword() : null,
                criteria != null && criteria.customerTypes() != null
                        ? String.join(",", criteria.customerTypes()) : null,
                null,
                attachmentTypes);
        Long taskId = stateService.createTask(filterSnapshot, operatorId);
        asyncExecutor.executeExport(taskId, criteria, attachmentTypes,
                filterSummary, System.currentTimeMillis());
        return new ExportTaskResult(taskId);
    }

    /**
     * 按 ids 模式创建合订本导出任务。
     *
     * @throws IllegalArgumentException 当 ids 为空或数量超过 {@link PerformanceBundleExportProperties#getMaxExportRecords()}
     */
    public ExportTaskResult exportByIds(List<Long> ids, Set<String> attachmentTypes,
                                         Long operatorId) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids 不能为空");
        }
        int maxRecords = properties.getMaxExportRecords();
        if (ids.size() > maxRecords) {
            throw new IllegalArgumentException(
                    "勾选记录数 " + ids.size() + " 超过上限 "
                            + maxRecords
                            + "，请减少勾选数量后重试");
        }
        AttachmentFilter.validateTypes(attachmentTypes);
        String filterSnapshot = serializeIds(ids, attachmentTypes);
        String filterSummary = PerformanceBundleExportNotificationPublisher.buildFilterSummary(
                null, null, null, attachmentTypes);
        Long taskId = stateService.createTask(filterSnapshot, operatorId);
        asyncExecutor.executeExportByIds(taskId, ids, attachmentTypes,
                filterSummary, System.currentTimeMillis());
        return new ExportTaskResult(taskId);
    }

    private String serializeCriteria(PerformanceSearchCriteria criteria, Set<String> attachmentTypes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "criteria", criteria != null ? criteria : PerformanceSearchCriteria.empty(),
                    "attachmentTypes", attachmentTypes != null ? attachmentTypes : Set.of()
            ));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeIds(List<Long> ids, Set<String> attachmentTypes) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "ids", ids,
                    "attachmentTypes", attachmentTypes != null ? attachmentTypes : Set.of()
            ));
        } catch (JsonProcessingException e) {
            return "{\"ids\":" + ids + "}";
        }
    }

    public Page<PerformanceExportTaskEntity> listTasks(Long createdBy, Pageable pageable) {
        return exportTaskRepo.findByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
    }

    public PerformanceExportTaskEntity getTaskStatus(Long taskId, Long createdBy) {
        return exportTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在或无权限"));
    }

    public Path getExportFile(Long taskId, Long createdBy) throws IOException {
        return getExportFileWithTask(taskId, createdBy).path();
    }

    /**
     * 返回导出文件路径和任务实体，避免 Controller 重复查询。
     *
     * @since CO-602 PR 审查修复
     */
    public ExportFileResult getExportFileWithTask(Long taskId, Long createdBy) throws IOException {
        PerformanceExportTaskEntity task = exportTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在或无权限"));

        if (task.getStatus() != ExportStatus.COMPLETED) {
            throw new IllegalStateException("导出任务尚未完成");
        }
        if (task.getExpiresAt() != null && LocalDateTime.now().isAfter(task.getExpiresAt())) {
            throw new IllegalStateException("导出文件已过期");
        }
        if (task.getStoredFilePath() == null) {
            throw new IllegalStateException("导出文件路径为空");
        }

        Path path = Paths.get(task.getStoredFilePath()).normalize();
        // 白名单校验：stored_file_path 必须落在 exportRoot 子树内
        // defense-in-depth：虽然 stored_file_path 由系统生成，但 DB 被污染时仍可能读到任意系统文件
        if (!isWithinExportRoot(path)) {
            log.warn("导出文件路径不在 exportRoot 子树内，疑似 DB 污染: taskId={}, path={}",
                    taskId, path);
            throw new IllegalStateException("导出文件路径非法");
        }
        if (!Files.exists(path)) {
            throw new IllegalStateException("导出文件已被清理");
        }
        return new ExportFileResult(path, task);
    }

    /**
     * 判断绝对路径是否落在 exportRoot 子树内。
     * <p>exportRoot 相对路径时按 JVM 工作目录归一化为绝对路径，
     * 与 {@link PerformanceAttachmentStorageAppService#resolveLocalPath} 保持一致策略。
     */
    private boolean isWithinExportRoot(Path target) {
        return PathUtils.isWithinSubtree(target, properties.resolveAbsoluteRoot());
    }

    public record ExportTaskResult(Long taskId) {}

    /**
     * 导出文件下载结果（路径 + 任务实体），避免 Controller 重复查询。
     *
     * @since CO-602 PR 审查修复
     */
    public record ExportFileResult(Path path, PerformanceExportTaskEntity task) {}
}
