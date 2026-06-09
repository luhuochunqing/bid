package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.notification.outbound.event.NotificationCreatedEvent;
import com.xiyu.bid.warehouse.domain.WarehouseExportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseLedgerExportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseLedgerExportPolicy.Section;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 台账导出应用服务 — 19 列精简版（无附件、无系统字段）。
 * 复用 warehouse_export_task 表与异步/通知/下载链路。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WarehouseLedgerExportAppService {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofDays(7);

    private final WarehouseExportTaskRepository exportTaskRepo;
    private final WarehouseFilterService filterService;
    private final WarehouseExcelWriter excelWriter;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${warehouse.export.root:/tmp/warehouse-exports}")
    private String exportRoot;

    public record ExportRequest(String scope, List<Long> ids, WarehouseFilterDTO filter, Set<Section> sections) {}

    @Transactional
    public WarehouseExportAppService.ExportTaskResult trigger(ExportRequest req, Long operatorId) {
        WarehouseExportTaskEntity task = WarehouseExportTaskEntity.builder()
                .status(ExportStatus.PENDING)
                .filterSnapshot(serialize(req))
                .createdBy(operatorId)
                .createdAt(LocalDateTime.now())
                .build();
        exportTaskRepo.save(task);
        executeLedgerAsync(task.getId(), req, operatorId, System.currentTimeMillis());
        return new WarehouseExportAppService.ExportTaskResult(task.getId());
    }

    @Async("warehouseExportExecutor")
    public void executeLedgerAsync(Long taskId, ExportRequest req, Long operatorId, long startMs) {
        try {
            markProcessing(taskId);
            List<WarehouseEntity> entities = loadEntities(req);
            String[] headers = WarehouseLedgerExportPolicy.getHeaders(req.sections());
            List<String[]> rows = WarehouseLedgerExportPolicy.buildRows(entities, req.sections());
            byte[] xlsx = excelWriter.write(headers, rows);
            String filePath = saveXlsx(taskId, xlsx);
            complete(taskId, operatorId, entities.size(), req, filePath, startMs);
        } catch (RuntimeException e) {
            log.error("台账导出失败: taskId={}", taskId, e);
            fail(taskId, truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("台账导出文件IO异常: taskId={}", taskId, e);
            fail(taskId, "文件写入失败: " + e.getMessage());
        }
    }

    private List<WarehouseEntity> loadEntities(ExportRequest req) {
        if ("ids".equals(req.scope())) {
            if (req.ids() == null || req.ids().isEmpty()) return List.of();
            return filterService.findAllByIds(req.ids());
        }
        if ("all_in_use".equals(req.scope())) {
            WarehouseFilterDTO f = new WarehouseFilterDTO(
                    null, null, List.of(WarehouseStatus.IN_USE), null, null, null, null,
                    null, null, null, null);
            return filterService.filterAll(f);
        }
        // 默认: current filter
        return filterService.filterAll(req.filter() != null ? req.filter() : null);
    }

    private String saveXlsx(Long taskId, byte[] xlsx) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        String filename = "warehouse_ledger_" + taskId + "_" + ts + ".xlsx";
        Path target = dir.resolve(filename);
        Files.write(target, xlsx);
        return target.toString();
    }

    private void complete(Long taskId, Long operatorId, int totalCount, ExportRequest req,
                          String filePath, long startMs) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        LocalDateTime now = LocalDateTime.now();
        WarehouseExportTaskEntity task = exportTaskRepo.findById(taskId).orElseThrow();
        task.setStatus(ExportStatus.COMPLETED);
        task.setTotalCount(totalCount);
        task.setStoredFilePath(filePath);
        task.setDownloadUrl("/api/knowledge/warehouses/export/tasks/" + taskId + "/download");
        task.setExpiresAt(now.plus(FILE_TTL));
        task.setCompletedAt(now);
        task.setResultSummary(buildSummary(totalCount, req, elapsedMs));
        exportTaskRepo.save(task);
        publish(task, totalCount, req, elapsedMs);
    }

    private String buildSummary(int totalCount, ExportRequest req, long elapsedMs) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("format", "ledger");
        map.put("totalCount", totalCount);
        map.put("scope", req.scope());
        map.put("sections", req.sections());
        map.put("elapsedMs", elapsedMs);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return null;
        }
    }

    private void publish(WarehouseExportTaskEntity task, int totalCount, ExportRequest req, long elapsedMs) {
        try {
            String title = "📤 仓库台账导出 — 完成";
            String body = String.format("仓库台账-%s.xlsx（%d 条；%d 秒；范围：%s）",
                    task.getCompletedAt().format(TS_FMT), totalCount, elapsedMs / 1000, scopeLabel(req));
            eventPublisher.publishEvent(new NotificationCreatedEvent(
                    null, List.of(task.getCreatedBy()),
                    "WAREHOUSE_LEDGER_EXPORT", title,
                    "WAREHOUSE_LEDGER_EXPORT_TASK", task.getId()));
        } catch (RuntimeException e) {
            log.warn("台账导出通知发布失败: taskId={}, error={}", task.getId(), e.getMessage());
        }
    }

    private String scopeLabel(ExportRequest req) {
        return switch (req.scope() == null ? "filter" : req.scope()) {
            case "ids" -> "当前勾选";
            case "all_in_use" -> "全部使用中";
            default -> "当前筛选";
        };
    }

    private void markProcessing(Long taskId) {
        exportTaskRepo.findById(taskId).ifPresent(t -> {
            t.setStatus(ExportStatus.PROCESSING);
            exportTaskRepo.save(t);
        });
    }

    private void fail(Long taskId, String reason) {
        exportTaskRepo.findById(taskId).ifPresent(t -> {
            t.setStatus(ExportStatus.FAILED);
            t.setFailureReason(reason);
            t.setCompletedAt(LocalDateTime.now());
            exportTaskRepo.save(t);
        });
    }

    private String serialize(ExportRequest req) {
        Map<String, Object> map = new HashMap<>();
        map.put("format", "ledger");
        map.put("scope", req.scope());
        map.put("ids", req.ids());
        map.put("sections", req.sections());
        map.put("filter", req.filter());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
