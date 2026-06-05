// Input: MySQL 任务队列表、共享文件系统状态和运行资源信号
// Output: 任务抢占、执行、重试与 DLQ 落库
// Pos: TenderUpload/Service
// 维护声明: 保持幂等与限流语义稳定，处理流程细节可独立演进.
package com.xiyu.bid.tenderupload.service;

import com.xiyu.bid.tenderupload.config.TenderProcessingProperties;
import com.xiyu.bid.tenderupload.entity.TenderFile;
import com.xiyu.bid.tenderupload.entity.TenderFileUploadStatus;
import com.xiyu.bid.tenderupload.entity.TenderTask;
import com.xiyu.bid.tenderupload.entity.TenderTaskDlq;
import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import com.xiyu.bid.tenderupload.repository.TenderFileRepository;
import com.xiyu.bid.tenderupload.repository.TenderTaskDlqRepository;
import com.xiyu.bid.tenderupload.repository.TenderTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
@ConditionalOnProperty(prefix = "app.tender-processing", name = "worker-enabled", havingValue = "true", matchIfMissing = false)
@RequiredArgsConstructor
@Slf4j
public class TenderTaskWorkerService {

    private static final int DEFAULT_TASK_LIMIT = 16;

    private final TenderProcessingProperties properties;
    private final TenderTaskRepository tenderTaskRepository;
    private final TenderTaskDlqRepository tenderTaskDlqRepository;
    private final TenderFileRepository tenderFileRepository;
    private final TenderTaskStateMachine taskStateMachine;
    private final StorageGuardService storageGuardService;
    private final TenderTaskClaimLock claimLock;
    private final TransactionTemplate transactionTemplate;
    @Qualifier("applicationTaskExecutor")
    private final Executor applicationTaskExecutor;

    @Scheduled(fixedDelayString = "${app.tender-processing.worker-fixed-delay-ms:5000}")
    public void pollAndProcess() {
        if (shouldPauseForResourceProtection()) {
            log.warn("Tender worker paused due to resource pressure");
            return;
        }

        String workerId = "tender-worker-" + UUID.randomUUID().toString().substring(0, 8);
        List<TenderTask> tasks = transactionTemplate.execute(status -> claimRunnableTasks(workerId, DEFAULT_TASK_LIMIT));
        if (tasks == null) {
            tasks = List.of();
        }
        if (tasks.isEmpty()) {
            return;
        }

        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (TenderTask task : tasks) {
            futures.add(CompletableFuture.runAsync(() ->
                    transactionTemplate.executeWithoutResult(status -> processTask(task.getId(), workerId)),
                    applicationTaskExecutor));
        }
        futures.forEach(future -> {
            try {
                future.join();
            } catch (RuntimeException ex) {
                log.error("Tender async execution failed: {}", ex.getMessage(), ex);
            }
        });
    }

    @Transactional
    public List<TenderTask> claimRunnableTasks(String workerId, int limit) {
        if (limit <= 0) {
            return List.of();
        }

        if (!claimLock.tryAcquire()) {
            return List.of();
        }

        try {
            long running = tenderTaskRepository.countByStatus(TenderTaskStatus.RUNNING);
            int remainingGlobal = Math.max(0, properties.getMaxGlobalConcurrency() - (int) running);
            if (remainingGlobal <= 0) {
                return List.of();
            }

            List<Long> taskIds = tenderTaskRepository.claimRunnableTaskIds(
                    LocalDateTime.now(),
                    Math.min(remainingGlobal, limit),
                    Math.max(1, properties.getMaxPerUserConcurrency())
            );
            if (taskIds.isEmpty()) {
                return List.of();
            }

            List<TenderTask> tasks = tenderTaskRepository.findAllById(taskIds);
            LocalDateTime now = LocalDateTime.now();
            tasks.forEach(task -> taskStateMachine.markRunning(task, workerId, now));
            return tenderTaskRepository.saveAll(tasks);
        } finally {
            claimLock.release();
        }
    }

    @Transactional
    public void processTask(Long taskId, String workerId) {
        TenderTask task = tenderTaskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalArgumentException("任务不存在: " + taskId));
        if (task.getStatus() != TenderTaskStatus.RUNNING) {
            return;
        }
        if (task.getLockedBy() == null || !task.getLockedBy().equals(workerId)) {
            return;
        }

        try {
            executePipeline(task);
            taskStateMachine.markSucceeded(task);
            tenderTaskRepository.save(task);
        } catch (RuntimeException ex) {
            TenderTaskStateMachine.RetryDecision decision = taskStateMachine.markRetryOrDlq(task, "PROCESSING_ERROR", ex.getMessage());
            tenderTaskRepository.save(task);
            if (decision.movedToDlq()) {
                TenderTaskDlq dlq = TenderTaskDlq.builder()
                        .taskId(task.getId())
                        .fileId(task.getFile().getId())
                        .failedAt(LocalDateTime.now())
                        .errorCode("PROCESSING_ERROR")
                        .errorMessage(ex.getMessage())
                        .payload(toPayload(task))
                        .build();
                tenderTaskDlqRepository.save(dlq);
            }
        }
    }

    private void executePipeline(TenderTask task) {
        TenderFile file = task.getFile();
        if (file == null) {
            throw new IllegalStateException("任务缺少文件关联");
        }
        Path absolutePath = storageGuardService.resolveAndValidate(file.getFilePath());
        storageGuardService.ensureExists(absolutePath);

        if (file.getFileSize() == null || file.getFileSize() <= 0L) {
            file.setFileSize(storageGuardService.fileSize(absolutePath));
        }

        if (file.getFileSha256() == null || file.getFileSha256().isBlank()) {
            String hash = storageGuardService.sha256(absolutePath);
            applyHashWithDedup(file, hash, task);
        }

        if (file.getUploadStatus() == TenderFileUploadStatus.DUPLICATE) {
            return;
        }

        if (file.getPageCount() == null || file.getPageCount() <= 0) {
            long size = file.getFileSize() == null ? 0L : file.getFileSize();
            int estimatedPages = (int) Math.max(1L, Math.ceil(size / 3500.0d));
            file.setPageCount(estimatedPages);
            tenderFileRepository.save(file);
        }

        // Pipeline steps kept explicit for observability and future extension.
        runParseStep(file);
        runOcrStep(file);
        runChunkStep(file);
        runIndexExtractStep(file);
        runSummaryStep(file);
    }

    private void applyHashWithDedup(TenderFile file, String hash, TenderTask currentTask) {
        file.setFileSha256(hash);
        try {
            tenderFileRepository.save(file);
        } catch (DataIntegrityViolationException ex) {
            TenderFile duplicate = tenderFileRepository.findByUserIdAndFileSha256(file.getUserId(), hash)
                    .filter(existing -> !existing.getId().equals(file.getId()))
                    .orElseThrow(() -> new IllegalStateException("文件去重冲突后未找到原始文件", ex));

            file.setUploadStatus(TenderFileUploadStatus.DUPLICATE);
            tenderFileRepository.save(file);
            TenderTask reusedTask = tenderTaskRepository.findByFile_Id(duplicate.getId()).orElse(null);
            String reused = reusedTask == null ? "none" : String.valueOf(reusedTask.getId());
            currentTask.setErrorCode("DEDUP_REUSED");
            currentTask.setErrorMessage("reused-task=" + reused);
        }
    }

    private String toPayload(TenderTask task) {
        Map<String, Object> payload = Map.of(
                "taskId", task.getId(),
                "fileId", task.getFile().getId(),
                "attempts", task.getAttempts(),
                "status", task.getStatus().name(),
                "availableAt", String.valueOf(task.getAvailableAt())
        );
        return payload.toString();
    }

    private void runParseStep(TenderFile file) {
        assertFileState(file, "PARSE");
    }

    private void runOcrStep(TenderFile file) {
        assertFileState(file, "OCR");
    }

    private void runChunkStep(TenderFile file) {
        assertFileState(file, "CHUNK");
    }

    private void runIndexExtractStep(TenderFile file) {
        assertFileState(file, "EXTRACT");
    }

    private void runSummaryStep(TenderFile file) {
        assertFileState(file, "SUMMARY");
    }

    private void assertFileState(TenderFile file, String stage) {
        if (file.getFilePath() == null || file.getFilePath().isBlank()) {
            throw new IllegalStateException("Stage " + stage + " failed: missing file path");
        }
    }

    private boolean shouldPauseForResourceProtection() {
        double cpuLoad = currentCpuLoad();
        if (cpuLoad >= properties.getCpuThreshold()) {
            return true;
        }
        double memoryUsage = currentMemoryUsage();
        return memoryUsage >= properties.getMemoryThreshold();
    }

    private double currentCpuLoad() {
        java.lang.management.OperatingSystemMXBean bean = ManagementFactory.getOperatingSystemMXBean();
        if (bean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            double load = sunBean.getCpuLoad();
            if (load >= 0d) {
                return load;
            }
        }
        return 0d;
    }

    private double currentMemoryUsage() {
        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        long used = memoryMXBean.getHeapMemoryUsage().getUsed();
        long max = memoryMXBean.getHeapMemoryUsage().getMax();
        if (max <= 0L) {
            return 0d;
        }
        return (double) used / (double) max;
    }

}
