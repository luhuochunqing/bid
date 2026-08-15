package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.repository.ScoreParseTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * AI 评分解析/打分任务状态机服务（spec 041）。
 * <p>集中管理 {@link ScoreParseTask} 状态跃迁，每次状态变更是独立 {@code @Transactional}。
 * <p>参考 TenderImportTaskStateService（spec 031）的状态机模式。
 * <p>状态机：PENDING → PROCESSING → COMPLETED / FAILED（终态不回退）。
 *
 * @see com.xiyu.bid.tender.service.TenderImportTaskStateService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreParseTaskStateService {

    private final ScoreParseTaskRepository taskRepository;
    private final ScoreParseProgressService progressService;

    /** 创建 PENDING 任务 */
    @Transactional
    public ScoreParseTask createTask(String taskId, Long projectId, String taskType,
                                     String fileName, String fileUrl) {
        ScoreParseTask task = ScoreParseTask.builder()
                .taskId(taskId)
                .projectId(projectId)
                .taskType(taskType)
                .status("PENDING")
                .progress(0)
                .timeoutMarked(false)
                .fileName(fileName)
                .fileUrl(fileUrl)
                .build();
        return taskRepository.save(task);
    }

    /** PENDING → PROCESSING */
    @Transactional
    public void markProcessing(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("PROCESSING");
            task.setStartedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** 更新进度（progress 0-100 + stage 描述），供轮询查询 */
    @Transactional
    public void updateProgress(String taskId, int progress, String stage) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setProgress(Math.max(0, Math.min(100, progress)));
            task.setStage(stage);
            taskRepository.save(task);
        });
    }

    /** PROCESSING → COMPLETED */
    @Transactional
    public void markCompleted(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("COMPLETED");
            task.setProgress(100);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** 超时终态文案（契约 §错误形态统一） */
    public static final String TIMEOUT_MESSAGE = "任务超时终止，保留上次成功结果";

    /**
     * 超时扫描专用（FR-020）：FAILED + timeout_marked=1 + 契约超时文案。
     */
    @Transactional
    public void markTimeout(String taskId) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("FAILED");
            task.setTimeoutMarked(true);
            task.setErrorMessage(TIMEOUT_MESSAGE);
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /** 任何状态 → FAILED */
    @Transactional
    public void markFailed(String taskId, String errorMessage) {
        taskRepository.findByTaskId(taskId).ifPresent(task -> {
            task.setStatus("FAILED");
            task.setErrorMessage(truncateMessage(errorMessage));
            task.setCompletedAt(LocalDateTime.now());
            taskRepository.save(task);
        });
    }

    /**
     * 三层降级失败标记（spec 031 范式）。
     * <p>层 1: 完整 save（含 error_message）
     * <p>层 2: 仅更新 status=FAILED
     * <p>层 3: 清 Redis，任务在 DB 中保持 PROCESSING（等待超时扫描/启动恢复标记 FAILED）
     */
    public void failTask(String taskId, String errorMessage) {
        try {
            markFailed(taskId, errorMessage);
            log.info("评分任务标记 FAILED 成功: taskId={}", taskId);
            return;
        } catch (RuntimeException e) {
            log.error("层 1 失败 - markFailed 异常: taskId={}, error={}", taskId, e.getMessage());
        }
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
        try {
            progressService.clearProgress(taskId);
            log.error("层 3 - 已清 Redis，任务保持 PROCESSING，等待扫描恢复: taskId={}", taskId);
        } catch (RuntimeException e) {
            log.error("层 3 失败 - 清 Redis 异常: taskId={}, error={}", taskId, e.getMessage());
        }
    }

    private String truncateMessage(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
