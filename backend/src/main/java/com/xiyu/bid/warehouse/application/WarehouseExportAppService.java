package com.xiyu.bid.warehouse.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.warehouse.domain.WarehouseExportPolicy;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExcelWriter;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity.ExportStatus;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskRepository;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.List;

/**
 * 仓库台账导出应用服务 — 只做编排，不含业务规则。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportAppService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofHours(24);

    private final WarehouseExportTaskRepository exportTaskRepo;
    private final WarehouseFilterService filterService;
    private final WarehouseExcelWriter excelWriter;
    private final ObjectMapper objectMapper;

    @Value("${warehouse.export.root:/tmp/warehouse-exports}")
    private String exportRoot;

    /**
     * 创建导出任务，触发异步执行。
     */
    @Transactional
    public ExportTaskResult export(WarehouseFilterDTO filterDTO, Long operatorId) {
        String filterSnapshot;
        try {
            filterSnapshot = objectMapper.writeValueAsString(filterDTO);
        } catch (JsonProcessingException e) {
            filterSnapshot = "{}";
        }

        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(filterSnapshot)
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
        exportTaskRepo.save(task);

        executeExportAsync(task.getId(), filterDTO, operatorId);

        return new ExportTaskResult(task.getId());
    }

    /**
     * 异步执行导出流程：查询 → 构建行数据 → 写 Excel → 保存文件 → 更新状态。
     */
    @Async("warehouseExportExecutor")
    public void executeExportAsync(Long taskId, WarehouseFilterDTO filterDTO, Long operatorId) {
        try {
            // 更新为 PROCESSING
            WarehouseExportTaskEntity task = exportTaskRepo.findById(taskId).orElseThrow();
            task.setStatus(ExportStatus.PROCESSING);
            exportTaskRepo.save(task);

            // 查询数据
            List<WarehouseEntity> entities = filterService.filterAll(filterDTO);
            int totalCount = entities.size();

            // 构建行数据并写 Excel
            List<String[]> rows = WarehouseExportPolicy.buildRows(entities);
            byte[] excelBytes = excelWriter.write(WarehouseExportPolicy.HEADERS, rows);

            // 保存文件
            String filePath = saveFile(taskId, excelBytes);

            // 更新任务为 COMPLETED
            LocalDateTime now = LocalDateTime.now();
            task.setStatus(ExportStatus.COMPLETED);
            task.setTotalCount(totalCount);
            task.setStoredFilePath(filePath);
            task.setDownloadUrl("/api/knowledge/warehouses/export/tasks/" + taskId + "/download");
            task.setExpiresAt(now.plusHours(24));
            task.setCompletedAt(now);
            exportTaskRepo.save(task);

        } catch (RuntimeException e) {
            log.error("仓库台账导出任务执行失败: taskId={}", taskId, e);
            failTask(taskId, truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库台账导出文件IO异常: taskId={}", taskId, e);
            failTask(taskId, "文件写入失败: " + e.getMessage());
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
        WarehouseExportTaskEntity task = exportTaskRepo.findByIdAndCreatedBy(taskId, createdBy)
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

        Path path = Paths.get(task.getStoredFilePath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("导出文件已被清理");
        }
        return Files.readAllBytes(path);
    }

    private String saveFile(Long taskId, byte[] bytes) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        String filename = "warehouse_" + taskId + "_" + ts + ".xlsx";
        Path filePath = dir.resolve(filename);
        Files.write(filePath, bytes);
        return filePath.toString();
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
