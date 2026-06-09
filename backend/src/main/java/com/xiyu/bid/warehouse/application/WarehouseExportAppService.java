package com.xiyu.bid.warehouse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.warehouse.domain.WarehouseExportPolicy;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExcelWriter;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 仓库台账导出应用服务 — 编排查询、Excel 生成、ZIP 打包、通知。
 * 不含业务规则；业务转换在 WarehouseExportPolicy 纯核心。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportAppService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofDays(7);

    private final WarehouseExportTaskRepository exportTaskRepo;
    private final WarehouseFilterService filterService;
    private final WarehouseExcelWriter excelWriter;
    private final WarehouseAttachmentRepository attachmentRepo;
    private final WarehouseExportZipBuilder zipBuilder;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${warehouse.export.root:/tmp/warehouse-exports}")
    private String exportRoot;

    @Transactional
    public ExportTaskResult export(WarehouseFilterDTO filterDTO, Long operatorId) {
        String filterSnapshot = serializeFilter(filterDTO);
        WarehouseExportTaskEntity task = createTask(filterSnapshot, operatorId);
        executeExportAsync(task.getId(), filterDTO, operatorId, System.currentTimeMillis());
        return new ExportTaskResult(task.getId());
    }

    @Transactional
    public ExportTaskResult exportByIds(List<Long> ids, Long operatorId) {
        String filterSnapshot = serializeIds(ids);
        WarehouseExportTaskEntity task = createTask(filterSnapshot, operatorId);
        executeExportByIdsAsync(task.getId(), ids, operatorId, System.currentTimeMillis());
        return new ExportTaskResult(task.getId());
    }

    @Async("warehouseExportExecutor")
    public void executeExportAsync(Long taskId, WarehouseFilterDTO filterDTO, Long operatorId, long startMs) {
        try {
            markProcessing(taskId);
            List<WarehouseEntity> entities = filterService.filterAll(filterDTO);
            doExport(taskId, operatorId, entities, filterDTO, startMs);
        } catch (RuntimeException e) {
            log.error("仓库台账导出任务执行失败: taskId={}", taskId, e);
            failTask(taskId, truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库台账导出文件IO异常: taskId={}", taskId, e);
            failTask(taskId, "文件写入失败: " + e.getMessage());
        }
    }

    @Async("warehouseExportExecutor")
    public void executeExportByIdsAsync(Long taskId, List<Long> ids, Long operatorId, long startMs) {
        try {
            markProcessing(taskId);
            List<WarehouseEntity> entities = filterService.findAllByIds(ids);
            doExport(taskId, operatorId, entities, null, startMs);
        } catch (RuntimeException e) {
            log.error("仓库按ID批量导出任务执行失败: taskId={}", taskId, e);
            failTask(taskId, truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库按ID批量导出文件IO异常: taskId={}", taskId, e);
            failTask(taskId, "文件写入失败: " + e.getMessage());
        }
    }

    private void doExport(Long taskId, Long operatorId, List<WarehouseEntity> entities,
                          WarehouseFilterDTO filterDTO, long startMs) throws IOException {
        Map<Long, List<WarehouseAttachmentEntity>> attachmentsByWhId = loadAttachments(entities);
        List<String[]> rows = WarehouseExportPolicy.buildRows(entities, attachmentsByWhId);
        byte[] xlsxBytes = excelWriter.write(WarehouseExportPolicy.HEADERS, rows);
        WarehouseExportZipBuilder.ZipBuildResult zip = zipBuilder.buildZip(xlsxBytes, entities, attachmentsByWhId);
        try {
            String filePath = saveZip(taskId, zip);
            completeTask(taskId, operatorId, entities.size(), filePath, zip, filterDTO, startMs);
        } finally {
            try { Files.deleteIfExists(zip.zipFile()); } catch (IOException ignored) { }
        }
    }

    private void markProcessing(Long taskId) {
        exportTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ExportStatus.PROCESSING);
            exportTaskRepo.save(task);
        });
    }

    private Map<Long, List<WarehouseAttachmentEntity>> loadAttachments(List<WarehouseEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        List<Long> ids = entities.stream().map(WarehouseEntity::getId).toList();
        return attachmentRepo.findByWarehouseIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getWarehouse().getId()));
    }

    private String saveZip(Long taskId, WarehouseExportZipBuilder.ZipBuildResult zip) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        String filename = "warehouse_export_" + taskId + "_" + ts + ".zip";
        Path target = dir.resolve(filename);
        Files.move(zip.zipFile(), target);
        return target.toString();
    }

    private void completeTask(Long taskId, Long operatorId, int totalCount, String filePath,
                              WarehouseExportZipBuilder.ZipBuildResult zip,
                              WarehouseFilterDTO filterDTO, long startMs) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        LocalDateTime now = LocalDateTime.now();
        WarehouseExportTaskEntity task = exportTaskRepo.findById(taskId).orElseThrow();
        task.setStatus(ExportStatus.COMPLETED);
        task.setTotalCount(totalCount);
        task.setStoredFilePath(filePath);
        task.setDownloadUrl("/api/knowledge/warehouses/export/tasks/" + taskId + "/download");
        task.setExpiresAt(now.plus(FILE_TTL));
        task.setCompletedAt(now);
        task.setResultSummary(buildResultSummaryJson(totalCount, zip, filterDTO, elapsedMs));
        exportTaskRepo.save(task);
        publishExportDoneNotification(task, totalCount, zip, filterDTO, elapsedMs);
    }

    private String buildResultSummaryJson(int totalCount, WarehouseExportZipBuilder.ZipBuildResult zip,
                                          WarehouseFilterDTO filterDTO, long elapsedMs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("totalCount", totalCount);
        map.put("xlsxBytes", zip.stats().xlsxBytes);
        map.put("zipBytes", zip.totalBytes());
        map.put("propertyCertCount", zip.stats().propertyCertCount);
        map.put("invoiceCount", zip.stats().invoiceCount);
        map.put("photosCount", zip.stats().photosCount);
        map.put("elapsedMs", elapsedMs);
        map.put("filterSummary", buildFilterSummary(filterDTO));
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static String buildFilterSummary(WarehouseFilterDTO filterDTO) {
        if (filterDTO == null) return "勾选模式";
        List<String> tags = new ArrayList<>();
        if (filterDTO.keyword() != null && !filterDTO.keyword().isBlank()) tags.add("关键词:" + filterDTO.keyword());
        if (filterDTO.types() != null && !filterDTO.types().isEmpty()) tags.add("类型:" + filterDTO.types());
        if (filterDTO.statuses() != null && !filterDTO.statuses().isEmpty()) tags.add("状态:" + filterDTO.statuses());
        if (filterDTO.province() != null) tags.add("省份:" + filterDTO.province());
        if (filterDTO.endDateFrom() != null || filterDTO.endDateTo() != null) {
            tags.add("到期:" + (filterDTO.endDateFrom() == null ? "..." : filterDTO.endDateFrom())
                    + " ~ " + (filterDTO.endDateTo() == null ? "..." : filterDTO.endDateTo()));
        }
        if (filterDTO.hasPropertyCert() != null && filterDTO.hasPropertyCert()) tags.add("有产权证");
        if (filterDTO.hasInvoice() != null && filterDTO.hasInvoice()) tags.add("有发票");
        if (filterDTO.hasPhotos() != null && filterDTO.hasPhotos()) tags.add("有照片");
        if (filterDTO.contactPersonKeyword() != null) tags.add("联系人:" + filterDTO.contactPersonKeyword());
        return tags.isEmpty() ? "全部" : "全部（" + String.join("，", tags) + "）";
    }

    private void publishExportDoneNotification(WarehouseExportTaskEntity task, int totalCount,
                                               WarehouseExportZipBuilder.ZipBuildResult zip,
                                               WarehouseFilterDTO filterDTO, long elapsedMs) {
        try {
            String title = "📤 仓库信息导出包 — 完成";
            String body = String.format(
                    "仓库信息导出包_%s.zip（%d 条，含 %d 份产权证 / %d 份发票 / %d 张照片；耗时 %d 秒；%s）",
                    task.getCompletedAt() != null ? task.getCompletedAt().format(TS_FMT) : "",
                    totalCount,
                    zip.stats().propertyCertCount, zip.stats().invoiceCount, zip.stats().photosCount,
                    elapsedMs / 1000,
                    buildFilterSummary(filterDTO));
            eventPublisher.publishEvent(new NotificationCreatedEvent(
                    null,
                    List.of(task.getCreatedBy()),
                    "WAREHOUSE_EXPORT",
                    title,
                    "WAREHOUSE_EXPORT_TASK",
                    task.getId()
            ));
            log.info("仓库导出完成通知已发布: taskId={}, totalCount={}, elapsedMs={}",
                    task.getId(), totalCount, elapsedMs);
        } catch (RuntimeException e) {
            log.warn("发布仓库导出完成通知失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    private WarehouseExportTaskEntity createTask(String filterSnapshot, Long operatorId) {
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(filterSnapshot)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
        exportTaskRepo.save(task);
        return task;
    }

    private String serializeFilter(WarehouseFilterDTO filterDTO) {
        try {
            return objectMapper.writeValueAsString(filterDTO);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private String serializeIds(List<Long> ids) {
        try {
            return objectMapper.writeValueAsString(Map.of("ids", ids));
        } catch (JsonProcessingException e) {
            return "{\"ids\":" + ids + "}";
        }
    }

    public Page<WarehouseExportTaskEntity> listTasks(Long createdBy, Pageable pageable) {
        return exportTaskRepo.findByCreatedByOrderByCreatedAtDesc(createdBy, pageable);
    }

    public WarehouseExportTaskEntity getTaskStatus(Long taskId, Long createdBy) {
        return exportTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
                .orElseThrow(() -> new IllegalArgumentException("导出任务不存在或无权限"));
    }

    public byte[] getExportFile(Long taskId, Long createdBy) throws IOException {
        WarehouseExportTaskEntity task = getTaskStatus(taskId, createdBy);
        if (task.getStatus() != ExportStatus.COMPLETED) {
            throw new IllegalStateException("导出任务尚未完成");
        }
        if (task.getExpiresAt() != null && LocalDateTime.now().isAfter(task.getExpiresAt())) {
            throw new IllegalStateException("导出文件已过期");
        }
        if (task.getStoredFilePath() == null) {
            throw new IllegalStateException("导出文件路径为空");
        }
        Path path = Paths.get(task.getStoredFilePath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("导出文件已被清理");
        }
        return Files.readAllBytes(path);
    }

    private void failTask(Long taskId, String reason) {
        exportTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ExportStatus.FAILED);
            task.setFailureReason(reason);
            task.setCompletedAt(LocalDateTime.now());
            exportTaskRepo.save(task);
        });
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }

    public record ExportTaskResult(Long taskId) {}
}
