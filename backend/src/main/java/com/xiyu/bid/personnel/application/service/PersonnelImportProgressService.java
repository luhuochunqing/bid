package com.xiyu.bid.personnel.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.personnel.domain.model.importtask.PersonnelImportTask;
import com.xiyu.bid.personnel.domain.port.PersonnelImportTaskRepository;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelImportErrorReportGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
class PersonnelImportProgressService {

    private static final String REDIS_KEY_PREFIX = "personnel:import:progress:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PersonnelImportTaskRepository importTaskRepository;
    private final PersonnelImportErrorReportGenerator errorReportGenerator;

    public String generateTaskNo() {
        String datePart = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "IMP-PER-" + datePart + "-" + randomPart;
    }

    public void updateProgress(Long taskId, String message, int percent) {
        String key = REDIS_KEY_PREFIX + taskId;
        try {
            ImportProgress progress = new ImportProgress(
                    "PROCESSING", percent, message, 0, 0, 0
            );
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(progress), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("更新进度失败", e);
        }
    }

    public ImportProgress getProgress(Long taskId) {
        String key = REDIS_KEY_PREFIX + taskId;
        String progressJson = redisTemplate.opsForValue().get(key);
        if (progressJson != null) {
            try {
                return objectMapper.readValue(progressJson, ImportProgress.class);
            } catch (JsonProcessingException e) {
                log.warn("解析进度JSON失败", e);
            }
        }

        PersonnelImportTask task = importTaskRepository.findById(taskId).orElse(null);
        if (task != null) {
            return new ImportProgress(
                    task.status().name(),
                    100,
                    task.status().name(),
                    task.totalCount(),
                    task.successCount(),
                    task.failureCount()
            );
        }

        return new ImportProgress("NOT_FOUND", 0, "任务不存在", 0, 0, 0);
    }

    public void clearProgress(Long taskId) {
        String key = REDIS_KEY_PREFIX + taskId;
        redisTemplate.delete(key);
    }

    public String saveErrorReport(Long taskId, byte[] reportBytes) {
        String fileName = "import_error_" + taskId + "_" + System.currentTimeMillis() + ".xlsx";
        String dir = "data/personnel-import-reports";
        try {
            java.nio.file.Files.createDirectories(java.nio.file.Paths.get(dir));
            java.nio.file.Path path = java.nio.file.Paths.get(dir, fileName);
            java.nio.file.Files.write(path, reportBytes);
            return "/api/knowledge/personnel/import/" + taskId + "/report";
        } catch (IOException e) {
            log.error("保存错误报告失败", e);
            return null;
        }
    }

    public byte[] getErrorReport(Long taskId) throws IOException {
        PersonnelImportTask task = importTaskRepository.findById(taskId).orElse(null);
        if (task == null || task.errorDetails() == null || task.errorDetails().isEmpty()) {
            return errorReportGenerator.generateErrorReport(
                    new com.xiyu.bid.personnel.domain.importvalidation.ValidationResult(
                            List.of(), List.of()
                    )
            );
        }

        List<com.xiyu.bid.personnel.domain.importvalidation.ImportValidationError> errors =
                task.errorDetails().stream()
                        .map(e -> com.xiyu.bid.personnel.domain.importvalidation.ImportValidationError.of(
                                e.sheetName(), e.rowNumber(), e.employeeNumber(),
                                "", e.errorMessage()))
                        .toList();

        return errorReportGenerator.generateErrorReport(
                new com.xiyu.bid.personnel.domain.importvalidation.ValidationResult(errors, List.of())
        );
    }

    record ImportProgress(
            String status,
            int percent,
            String message,
            int totalCount,
            int successCount,
            int failureCount
    ) {}
}
