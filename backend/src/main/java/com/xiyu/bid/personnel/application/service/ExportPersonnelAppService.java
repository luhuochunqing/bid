package com.xiyu.bid.personnel.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.personnel.application.command.PersonnelListCriteria;
import com.xiyu.bid.personnel.application.dto.PersonnelDTO;
import com.xiyu.bid.personnel.application.mapper.PersonnelMapper;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog;
import com.xiyu.bid.personnel.domain.model.PersonnelOperationLog.ChangeDetail;
import com.xiyu.bid.personnel.domain.port.PersonnelRepository;
import com.xiyu.bid.personnel.infrastructure.excel.PersonnelZipExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExportPersonnelAppService {

    private static final String REDIS_KEY_PREFIX = "personnel:export:progress:";
    private static final String REDIS_OPERATOR_KEY = "personnel:export:operator:";
    private static final Duration REDIS_TTL = Duration.ofDays(7);
    private static final Duration FILE_TTL = Duration.ofDays(7);

    private final PersonnelRepository personnelRepository;
    private final PersonnelZipExporter zipExporter;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final PersonnelOperationLogService operationLogService;

    public ExportTaskInfo initiateExportTask(Long currentUserId, String operatorName) {
        String taskNo = generateTaskNo();
        String taskId = UUID.randomUUID().toString();

        // 存储 operatorName 以便异步完成时使用
        storeOperatorInfo(taskId, operatorName, currentUserId);

        return new ExportTaskInfo(taskId, taskNo);
    }

    @Async("importExportExecutor")
    public void executeExportAsync(String taskId, PersonnelListCriteria criteria, Long currentUserId) {
        try {
            updateProgress(taskId, "正在查询人员数据...", 10);

            List<PersonnelDTO> personnelList = personnelRepository.findAll(criteria).stream()
                    .map(this::toDTO)
                    .toList();

            updateProgress(taskId, "正在生成Excel...", 40);

            if (personnelList.isEmpty()) {
                completeExportTask(taskId, 0, null);
                return;
            }

            updateProgress(taskId, "正在打包ZIP文件...", 70);

            byte[] zipBytes = zipExporter.exportZip(personnelList);

            updateProgress(taskId, "正在保存文件...", 90);

            String downloadPath = saveExportFile(taskId, zipBytes);

            completeExportTask(taskId, personnelList.size(), downloadPath);

        } catch (IOException e) {
            log.error("导出任务执行失败: taskId={}", taskId, e);
            failExportTask(taskId, e.getMessage());
        }
    }

    private PersonnelDTO toDTO(com.xiyu.bid.personnel.domain.model.Personnel personnel) {
        PersonnelMapper mapper = new PersonnelMapper();
        return mapper.toDTO(personnel);
    }

    private String saveExportFile(String taskId, byte[] zipBytes) throws IOException {
        String fileName = "personnel_export_" + taskId + "_" + System.currentTimeMillis() + ".zip";
        String dir = "data/personnel-exports";
        Path dirPath = Paths.get(dir);
        Files.createDirectories(dirPath);
        Path filePath = dirPath.resolve(fileName);
        Files.write(filePath, zipBytes);

        redisTemplate.opsForValue().set(
                "personnel:export:file:" + taskId,
                filePath.toString(),
                FILE_TTL
        );

        return "/api/knowledge/personnel/export/" + taskId + "/download";
    }

    public ExportProgress getProgress(String taskId) {
        String key = REDIS_KEY_PREFIX + taskId;
        String progressJson = redisTemplate.opsForValue().get(key);
        if (progressJson != null) {
            try {
                return objectMapper.readValue(progressJson, ExportProgress.class);
            } catch (JsonProcessingException e) {
                log.warn("解析进度JSON失败", e);
            }
        }

        String fileKey = "personnel:export:file:" + taskId;
        String filePath = redisTemplate.opsForValue().get(fileKey);
        if (filePath != null && Files.exists(Paths.get(filePath))) {
            return new ExportProgress("COMPLETED", 100, "导出完成", 0, filePath);
        }

        return new ExportProgress("NOT_FOUND", 0, "任务不存在或已过期", null, null);
    }

    public byte[] getExportFile(String taskId) throws IOException {
        String fileKey = "personnel:export:file:" + taskId;
        String filePath = redisTemplate.opsForValue().get(fileKey);

        if (filePath == null || !Files.exists(Paths.get(filePath))) {
            throw new IllegalArgumentException("导出文件不存在或已过期");
        }

        return Files.readAllBytes(Paths.get(filePath));
    }

    public void updateProgress(String taskId, String message, int percent) {
        String key = REDIS_KEY_PREFIX + taskId;
        try {
            ExportProgress progress = new ExportProgress("PROCESSING", percent, message, null, null);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(progress), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("更新进度失败", e);
        }
    }

    private void completeExportTask(String taskId, int recordCount, String downloadPath) {
        try {
            ExportProgress progress = new ExportProgress("COMPLETED", 100,
                    "导出完成，共 " + recordCount + " 条人员记录", recordCount, downloadPath);
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + taskId,
                    objectMapper.writeValueAsString(progress),
                    FILE_TTL
            );
        } catch (JsonProcessingException e) {
            log.warn("更新完成状态失败", e);
        }

        // 记录批量导出操作日志（PRD 4.3.1.8: 批量导出人员）
        recordExportLog(taskId, recordCount);
    }

    /** 存储操作人信息，供异步任务完成时记录日志使用 */
    private void storeOperatorInfo(String taskId, String operatorName, Long operatorId) {
        String key = REDIS_OPERATOR_KEY + taskId;
        try {
            ExportOperatorInfo info = new ExportOperatorInfo(operatorName, operatorId);
            redisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(info), REDIS_TTL);
        } catch (JsonProcessingException e) {
            log.warn("存储导出操作人信息失败", e);
        }
    }

    /** 获取操作人信息 */
    private ExportOperatorInfo getOperatorInfo(String taskId) {
        String key = REDIS_OPERATOR_KEY + taskId;
        String json = redisTemplate.opsForValue().get(key);
        if (json != null) {
            try {
                return objectMapper.readValue(json, ExportOperatorInfo.class);
            } catch (JsonProcessingException e) {
                log.warn("解析导出操作人信息JSON失败", e);
            }
        }
        return null;
    }

    /** 记录批量导出操作日志 */
    private void recordExportLog(String taskId, int recordCount) {
        ExportOperatorInfo opInfo = getOperatorInfo(taskId);
        String operatorName = opInfo != null ? opInfo.operatorName() : "system";
        Long operatorId = opInfo != null ? opInfo.operatorId() : 0L;

        List<ChangeDetail> changes = List.of(
                new ChangeDetail("recordCount", String.valueOf(recordCount), "")
        );

        operationLogService.save(PersonnelOperationLog.create(
                null, // 批量操作不绑定单一人员
                operatorId,
                operatorName,
                PersonnelOperationLog.OperationType.BATCH_EXPORT_PERSONNEL,
                changes
        ));
    }

    private void failExportTask(String taskId, String errorMessage) {
        try {
            ExportProgress progress = new ExportProgress("FAILED", 0, "导出失败: " + errorMessage, null, null);
            redisTemplate.opsForValue().set(
                    REDIS_KEY_PREFIX + taskId,
                    objectMapper.writeValueAsString(progress),
                    REDIS_TTL
            );
        } catch (JsonProcessingException e) {
            log.warn("更新失败状态失败", e);
        }
    }

    public void cleanupExpiredFiles() {
        String dir = "data/personnel-exports";
        Path dirPath = Paths.get(dir);
        if (!Files.exists(dirPath)) return;

        try {
            long expiryTime = System.currentTimeMillis() - FILE_TTL.toMillis();
            Files.list(dirPath)
                    .filter(path -> path.toFile().lastModified() < expiryTime)
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                            log.info("已清理过期导出文件: {}", path);
                        } catch (IOException e) {
                            log.warn("清理文件失败: {}", path, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("清理导出文件目录扫描失败", e);
        }
    }

    private String generateTaskNo() {
        String datePart = LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
        String randomPart = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return "EXP-PER-" + datePart + "-" + randomPart;
    }

    public record ExportTaskInfo(String taskId, String taskNo) {}

    public record ExportProgress(
            String status,
            int percent,
            String message,
            Integer recordCount,
            String downloadPath
    ) {}

    private record ExportOperatorInfo(String operatorName, Long operatorId) {}
}
