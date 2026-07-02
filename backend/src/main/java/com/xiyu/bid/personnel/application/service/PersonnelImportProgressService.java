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
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 人员导入进度服务
 *
 * CO-469 第三轮修复：去掉 @ConditionalOnBean(StringRedisTemplate.class)
 * 原因：@ConditionalOnBean 在 @Service 类（组件扫描）上不可靠，
 *       Spring Boot 评估条件时 StringRedisTemplate 可能尚未注册，
 *       导致 bean 不创建、Optional<PersonnelImportProgressService> 注入空值，
 *       进而使 ImportPersonnelAppService.getProgress() 走 fallback 返回 "UNKNOWN"，
 *       前端显示 "Redis not available"。
 *
 * 改用 Optional<StringRedisTemplate> 注入模式（与 ExportPersonnelAppService 一致），
 * 在 Redis 不可用时通过 Optional.empty() 优雅降级。
 */
@Service
@RequiredArgsConstructor
@Slf4j
class PersonnelImportProgressService {

    private static final String REDIS_KEY_PREFIX = "personnel:import:progress:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);

    private final Optional<StringRedisTemplate> redisTemplate;
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
            setRedisValue(key, objectMapper.writeValueAsString(progress), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("更新进度失败", e);
        }
    }

    public ImportProgress getProgress(Long taskId) {
        String key = REDIS_KEY_PREFIX + taskId;
        Optional<String> progressJson = getRedisValue(key);
        if (progressJson.isPresent()) {
            try {
                return objectMapper.readValue(progressJson.orElseThrow(), ImportProgress.class);
            } catch (JsonProcessingException e) {
                log.warn("解析进度JSON失败", e);
            }
        }

        // DB fallback：Redis 未命中或不可用时，从数据库读取任务状态
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
        redisTemplate.ifPresent(template -> {
            try {
                template.delete(key);
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip deleting key={}: {}", key, e.getMessage());
            }
        });
    }

    public String saveErrorReport(Long taskId, byte[] reportBytes) {
        String fileName = "import_error_" + taskId + "_" + System.currentTimeMillis() + ".xlsx";
        String dir = "data/personnel-import-reports";
        try {
            Files.createDirectories(Paths.get(dir));
            java.nio.file.Path path = Paths.get(dir, fileName);
            Files.write(path, reportBytes);
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

    /** 存储操作人信息，供异步任务完成时记录日志使用 */
    public void storeOperatorInfo(Long taskId, String operatorName, Long operatorId) {
        String key = "personnel:import:operator:" + taskId;
        try {
            OperatorInfo info = new OperatorInfo(operatorName, operatorId);
            setRedisValue(key, objectMapper.writeValueAsString(info), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("存储操作人信息失败", e);
        }
    }

    /** 获取操作人信息 */
    public OperatorInfo getOperatorInfo(Long taskId) {
        String key = "personnel:import:operator:" + taskId;
        Optional<String> json = getRedisValue(key);
        if (json.isPresent()) {
            try {
                return objectMapper.readValue(json.orElseThrow(), OperatorInfo.class);
            } catch (JsonProcessingException e) {
                log.warn("解析操作人信息JSON失败", e);
            }
        }
        return null;
    }

    private Optional<String> getRedisValue(String key) {
        return redisTemplate.flatMap(template -> {
            try {
                String value = template.opsForValue().get(key);
                return value != null ? Optional.of(value) : Optional.empty();
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip reading key={}: {}", key, e.getMessage());
                return Optional.empty();
            }
        });
    }

    private void setRedisValue(String key, String value, Duration ttl) {
        redisTemplate.ifPresent(template -> {
            try {
                template.opsForValue().set(key, value, ttl);
            } catch (RuntimeException e) {
                log.debug("Redis unavailable, skip writing key={}: {}", key, e.getMessage());
            }
        });
    }

    record ImportProgress(
            String status,
            int percent,
            String message,
            int totalCount,
            int successCount,
            int failureCount
    ) {}

    public record OperatorInfo(String operatorName, Long operatorId) {}
}
