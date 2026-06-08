package com.xiyu.bid.personnel.application.service;

import com.xiyu.bid.personnel.domain.importvalidation.ValidationResult;
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
    private final PersonnelImportProgressService progressService;
    private final PersonnelImportTaskRepository importTaskRepository;
    private final PersonnelImportErrorReportGenerator errorReportGenerator;

    @Transactional
    public PersonnelImportTask initiateImportTask(Long currentUserId) {
        String taskNo = progressService.generateTaskNo();
        PersonnelImportTask task = PersonnelImportTask.createNew(taskNo, currentUserId);
        return importTaskRepository.save(task);
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

        } catch (IOException e) {
            log.error("导入任务执行失败: taskId={}", taskId, e);
            failImportTask(taskId, e.getMessage());
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
    }

    private void failImportTask(Long taskId, String errorMessage) {
        PersonnelImportTask task = importTaskRepository.findById(taskId).orElse(null);
        if (task == null) return;

        List<ImportErrorDetail> errors = List.of(new ImportErrorDetail(
                "系统", null, null, null, errorMessage
        ));

        PersonnelImportTask updated = new PersonnelImportTask(
                task.id(), task.taskNo(), task.module(), ImportTaskStatus.FAILED,
                0, 0, 1, 0, errors, null, task.createdBy(),
                task.createdAt(), LocalDateTime.now()
        );
        importTaskRepository.save(updated);
        progressService.clearProgress(taskId);
    }

    public ImportProgressInfo getProgress(Long taskId) {
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
