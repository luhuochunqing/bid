package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.model.importtask.ImportErrorDetail;
import com.xiyu.bid.personnel.domain.model.importtask.ImportTaskStatus;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.domain.port.PersonnelImportTaskRepository;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelExcelImporter;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportErrorReportGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ImportPersonnelAppService {

    private final PersonnelExcelImporter excelImporter;
    private final PersonnelImportExecutor importExecutor;
    // CO-469 第三轮：PersonnelImportProgressService 现在永远创建（去掉了 @ConditionalOnBean），
    // 不再用 Optional 注入。Redis 不可用时在服务内部通过 Optional<StringRedisTemplate> 降级。
    private final PersonnelImportProgressService progressService;
    private final PersonnelImportTaskRepository importTaskRepository;
    private final PersonnelImportErrorReportGenerator errorReportGenerator;
    private final PersonnelOperationLogService operationLogService;

    @Transactional
    public PersonnelImportTask initiateImportTask(Long currentUserId, String operatorName) {
        String taskNo = progressService.generateTaskNo();
        PersonnelImportTask task = PersonnelImportTask.createNew(taskNo, currentUserId);
        task = importTaskRepository.save(task);

        final Long taskId = task.id();
        progressService.storeOperatorInfo(taskId, operatorName, currentUserId);

        return task;
    }

    @Async("importExportExecutor")
    public void executeImportAsync(Long taskId, MultipartFile file, Long currentUserId) {
        try {
            progressService.updateProgress(taskId, "正在解析Excel文件...", 5);

            PersonnelExcelImporter.ImportResult result = excelImporter.importFromStream(file.getInputStream());

            progressService.updateProgress(taskId, "正在校验数据...", 20);

            ValidationResult validationResult = result.validationResult();

            if (validationResult.hasBlockingErrors()) {
                handleValidationErrors(taskId, validationResult);
                return;
            }

            PersonnelImportExecutor.ImportResult importResult = importExecutor.executeImport(
                    result,
                    (message, percent) -> progressService.updateProgress(taskId, message, percent + 40)
            );

            completeImportTask(taskId, importResult, null);

        } catch (IOException | RuntimeException | Error e) {
            // CO-469 第四轮：catch 范围扩大到 Error
            // 原因：异步任务只要漏掉一类异常，线程就会静默终止，进度永远卡住。
            //       之前 catch (IOException | RuntimeException) 接不住 Error 等其他异常。
            //       按 lessons-learned 纪律：异步任务必须全兜底防止静默失败。
            log.error("导入任务执行失败: taskId={}", taskId, e);
            failImportTask(taskId, e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private void handleValidationErrors(Long taskId, ValidationResult validationResult) {
        try {
            byte[] errorReport = errorReportGenerator.generateErrorReport(validationResult);
            String reportUrl = progressService.saveErrorReport(taskId, errorReport);

            List<ImportErrorDetail> errorDetails = validationResult.errors().stream()
                    .map(e -> new ImportErrorDetail(
                            e.sheet(), e.rowNumber(), e.employeeNumber(),
                            null, e.field() + ": " + e.message()))
                    .toList();

            completeImportTask(taskId, new PersonnelImportExecutor.ImportResult(
                    validationResult.errors().size(), 0,
                    validationResult.errors().size(), 0, errorDetails
            ), reportUrl);

        } catch (IOException e) {
            log.error("生成错误报告失败", e);
            failImportTask(taskId, "校验失败且无法生成错误报告: " + e.getMessage());
        }
    }

    private void completeImportTask(Long taskId, PersonnelImportExecutor.ImportResult result, String reportUrl) {
        PersonnelImportTask task = importTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        ImportTaskStatus finalStatus = result.failureCount() > 0 && result.successCount() > 0
                ? ImportTaskStatus.PARTIAL_SUCCESS
                : result.failureCount() > 0 ? ImportTaskStatus.FAILED
                : ImportTaskStatus.COMPLETED;

        PersonnelImportTask updated = new PersonnelImportTask(
                task.id(), task.taskNo(), task.module(), finalStatus,
                result.totalCount(), result.successCount(), result.failureCount(),
                result.warningCount(),
                result.errorDetails(), reportUrl, task.createdBy(),
                task.createdAt(), LocalDateTime.now()
        );
        importTaskRepository.save(updated);
        progressService.clearProgress(taskId);

        recordImportLog(task, result);
    }

    private void recordImportLog(PersonnelImportTask task, PersonnelImportExecutor.ImportResult result) {
        PersonnelImportProgressService.OperatorInfo opInfo = progressService.getOperatorInfo(task.id());
        String operatorName = opInfo != null ? opInfo.operatorName() : "system";
        Long operatorId = opInfo != null ? opInfo.operatorId() : 0L;

        List<ChangeDetail> changes = List.of(
                new ChangeDetail("total", String.valueOf(result.totalCount()), ""),
                new ChangeDetail("success", String.valueOf(result.successCount()), ""),
                new ChangeDetail("failure", String.valueOf(result.failureCount()), "")
        );

        operationLogService.save(PersonnelOperationLog.create(
                null, // 批量操作不绑定单一人员
                operatorId,
                operatorName,
                PersonnelOperationLog.OperationType.BATCH_IMPORT_PERSONNEL,
                changes
        ));
    }

    // CO-469 第八轮：failImportTask 加防御性 try/catch + 降级
    // 历史根因：save() 写入 error_details 字段时若序列化失败（如 List.toString() 输出非合法 JSON），
    // 会抛 DataIntegrityViolationException，被 SimpleAsyncUncaughtExceptionHandler 吞掉，
    // 任务状态永久停在 PROCESSING/5%。修复方案：save 失败时降级到 updateStatus(FAILED)，
    // 仍失败则只清理 Redis 进度，保证 status 字段最终落库为 FAILED。
    private void failImportTask(Long taskId, String errorMessage) {
        PersonnelImportTask task = null;
        try {
            task = importTaskRepository.findById(taskId).orElse(null);
        } catch (RuntimeException findException) {
            log.warn("failImportTask findById 失败: taskId={}", taskId, findException);
        }
        if (task == null) {
            // 任务记录不存在，仍尝试清理 Redis 进度避免前端继续轮询
            safeClearProgress(taskId);
            return;
        }

        List<ImportErrorDetail> errors = List.of(new ImportErrorDetail(
                "系统", null, null, null, errorMessage
        ));

        PersonnelImportTask updated = new PersonnelImportTask(
                task.id(), task.taskNo(), task.module(), ImportTaskStatus.FAILED,
                0, 0, 1, 0, errors, null, task.createdBy(),
                task.createdAt(), LocalDateTime.now()
        );

        try {
            importTaskRepository.save(updated);
        } catch (RuntimeException saveException) {
            // 兜底：save 失败时降级到 updateStatus 仅写 status 字段（不涉及 error_details JSON 序列化）
            log.error("failImportTask save 失败，降级到 updateStatus: taskId={}", taskId, saveException);
            try {
                importTaskRepository.updateStatus(taskId, ImportTaskStatus.FAILED.name());
            } catch (RuntimeException fallbackException) {
                log.error("failImportTask updateStatus 兜底也失败: taskId={}", taskId, fallbackException);
            }
        }
        safeClearProgress(taskId);
    }

    private void safeClearProgress(Long taskId) {
        try {
            progressService.clearProgress(taskId);
        } catch (RuntimeException progressException) {
            log.warn("clearProgress 失败，前端轮询将通过 DB fallback 读到终态: taskId={}", taskId, progressException);
        }
    }

    public ImportProgressInfo getProgress(Long taskId) {
        // CO-469 第三轮：progressService 现在永远存在，
        // Redis 不可用时由 PersonnelImportProgressService 内部 DB fallback 处理
        PersonnelImportProgressService.ImportProgress progress = progressService.getProgress(taskId);
        return new ImportProgressInfo(
                progress.status(),
                progress.percent(),
                progress.message(),
                progress.totalCount(),
                progress.successCount(),
                progress.failureCount()
        );
    }

    public byte[] getErrorReport(Long taskId) throws IOException {
        return progressService.getErrorReport(taskId);
    }

    public record ImportProgressInfo(
            String status,
            int percent,
            String message,
            int totalCount,
            int successCount,
            int failureCount
    ) {}
}
