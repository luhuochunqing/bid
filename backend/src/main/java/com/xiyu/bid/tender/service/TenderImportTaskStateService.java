package com.xiyu.bid.tender.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标讯导入任务状态机服务。
 * <p>集中管理 {@link TenderImportTask} 状态跃迁，每次状态变更是独立 {@code @Transactional}。
 * <p>参考 WarehouseImportTaskStateService 的状态机模式。
 * <p>状态机：PENDING → PROCESSING → COMPLETED / PARTIAL_SUCCESS / FAILED
 *
 * @see com.xiyu.bid.warehouse.application.WarehouseImportTaskStateService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderImportTaskStateService {

    private final TenderImportTaskRepository taskRepository;
    private final ObjectMapper objectMapper;
    private final TenderImportProgressService progressService;

    /** 创建 PENDING 任务 */
    @Transactional
    public TenderImportTask createTask(String taskId, Long userId, String fileName) {
        TenderImportTask task = TenderImportTask.builder()
                .taskId(taskId)
                .userId(userId)
                .fileName(fileName)
                .totalRows(0)
                .processedRows(0)
                .successCount(0)
                .failureCount(0)
                .status("PENDING")
                .build();
        return taskRepository.save(task);
    }

    /** PENDING → PROCESSING */
    @Transactional
    public void markProcessing(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("PROCESSING");
            taskRepository.save(task);
        });
    }

    /** PROCESSING → COMPLETED（全部成功） */
    @Transactional
    public void markCompleted(String taskId, int totalRows) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("COMPLETED");
            task.setTotalRows(totalRows);
            task.setProcessedRows(totalRows);
            task.setSuccessCount(totalRows);
            task.setFailureCount(0);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** PROCESSING → PARTIAL_SUCCESS（部分成功） */
    @Transactional
    public void markPartialSuccess(String taskId, int totalRows, int successCount, int failureCount,
                                   List<TenderImportTaskError> errors) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("PARTIAL_SUCCESS");
            task.setTotalRows(totalRows);
            task.setProcessedRows(totalRows);
            task.setSuccessCount(successCount);
            task.setFailureCount(failureCount);
            task.setErrorDetails(serializeErrors(errors));
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** 任何状态 → FAILED */
    @Transactional
    public void markFailed(String taskId, List<TenderImportTaskError> errors) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("FAILED");
            task.setErrorDetails(serializeErrors(errors));
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /**
     * 三层降级失败标记（参考 ImportPersonnelAppService 范式）。
     * <p>层 1: 完整 save（含 error_details JSON）
     * <p>层 2: 仅更新 status=FAILED（error_details 序列化失败时）
     * <p>层 3: 清 Redis，任务在 DB 中保持 PROCESSING（下次启动扫描时标记 FAILED）
     *
     * @see com.xiyu.bid.personnel.service.ImportPersonnelAppService
     */
    public void failTaskWithThreeLayerFallback(String taskId, List<TenderImportTaskError> errors) {
        try {
            markFailed(taskId, errors);
            log.info("标讯导入任务标记 FAILED 成功: taskId={}", taskId);
            return;
        } catch (RuntimeException e) {
            log.error("层 1 失败 - markFailed 异常: taskId={}, error={}", taskId, e.getMessage());
        }
        // 层 2: 仅更新状态（不写 error_details）
        try {
            taskRepository.findByTaskId(taskId).ifPresent(task -> {
                task.setStatus("FAILED");
                task.setCompletedAt(LocalDateTime.now());
                taskRepository.save(task);
            });
            log.warn("层 2 成功 - 仅更新 status=FAILED: taskId={}", taskId);
            return;
        } catch (RuntimeException e) {
            log.error("层 2 失败 - 仅更新状态异常: taskId={}, error={}", taskId, e.getMessage());
        }
        // 层 3: 清 Redis，任务在 DB 中保持 PROCESSING，等待启动扫描标记 FAILED
        try {
            progressService.clearProgress(taskId);
            log.error("层 3 - 已清 Redis，任务保持 PROCESSING，等待启动扫描: taskId={}", taskId);
        } catch (RuntimeException e) {
            log.error("层 3 失败 - 清 Redis 异常: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    private String serializeErrors(List<TenderImportTaskError> errors) {
        if (errors == null || errors.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(errors);
        } catch (JsonProcessingException e) {
            log.warn("序列化 error_details 失败: {}", e.getMessage());
            return null;
        }
    }
}
