package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.domain.WarehouseLedgerExportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseLedgerExportPolicy.Section;
import com.xiyu.bid.warehouse.domain.WarehouseStatus;
import com.xiyu.bid.warehouse.dto.WarehouseFilterDTO;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentType;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseAttachmentRepository;
import com.xiyu.bid.warehouse.infrastructure.WarehouseEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExcelWriter;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportTaskEntity;
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 仓库台账导出异步执行器 — 承载 @Async 方法，从 WarehouseLedgerExportAppService 提取。
 *
 * <p>修复与 {@link WarehouseExportAsyncExecutor} 同源的 self-invocation 问题：
 * 原 WarehouseLedgerExportAppService.trigger() 在 @Transactional 内直接调用
 * this.executeLedgerAsync()，@Async 注解失效。
 *
 * <p>状态机委托 {@link WarehouseExportTaskStateService}，避免重复实现。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseLedgerExportAsyncExecutor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofDays(7);

    private final WarehouseExportTaskStateService stateService;
    private final WarehouseFilterService filterService;
    private final WarehouseExcelWriter excelWriter;
    private final WarehouseAttachmentRepository attachmentRepo;
    private final WarehouseExportZipBuilder zipBuilder;
    private final WarehouseLedgerExportNotificationPublisher ledgerPublisher;
    private final UserRepository userRepository;

    @Value("${warehouse.export.root:/tmp/warehouse-exports}")
    private String exportRoot;

    @Async("warehouseExportExecutor")
    public void executeLedger(Long taskId, WarehouseLedgerExportAppService.ExportRequest req,
                              Long operatorId, String operatorUsername, long startMs) {
        try {
            stateService.markProcessing(taskId);
            List<WarehouseEntity> entities = loadEntities(req);
            Map<Long, List<WarehouseAttachmentEntity>> attachmentsByWhId = loadLeaseContractAttachments(entities);
            Map<Long, String> usernameById = loadUsernames(entities);
            String[] headers = WarehouseLedgerExportPolicy.getHeaders(req.sections());
            List<String[]> rows = WarehouseLedgerExportPolicy.buildRows(entities, req.sections(), usernameById, attachmentsByWhId);
            byte[] xlsx = excelWriter.write(headers, rows);
            // 台账导出无 Word 合订本需求（CO-582 仅作用于仓库信息模块导出），传 null + 保留附件目录
            WarehouseExportZipBuilder.ZipBuildResult zip = zipBuilder.buildZip(xlsx, entities, attachmentsByWhId,
                    null, Set.of(WarehouseAttachmentOrganizationForm.ATTACHMENTS_FOLDER));
            try {
                String filePath = saveZip(taskId, zip);
                long elapsedMs = System.currentTimeMillis() - startMs;
                String resultSummary = buildSummary(entities.size(), req, elapsedMs, zip);
                WarehouseExportTaskEntity task = stateService.complete(new ExportCompletion(
                        taskId, entities.size(), filePath, resultSummary, FILE_TTL, startMs));
                ledgerPublisher.publish(task, entities.size(), req, elapsedMs);
            } finally {
                try { Files.deleteIfExists(zip.zipFile()); } catch (IOException ignored) { log.debug("Failed to delete zip file", ignored); }
            }
        } catch (RuntimeException e) {
            log.error("台账导出失败: taskId={}", taskId, e);
            stateService.fail(taskId, WarehouseExportTaskStateService.truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("台账导出文件IO异常: taskId={}", taskId, e);
            stateService.fail(taskId, "文件写入失败: " + e.getMessage());
        } catch (Error e) {
            log.error("台账导出遭遇 Error，尝试标记失败: taskId={}", taskId, e);
            stateService.fail(taskId, "系统资源异常: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    private List<WarehouseEntity> loadEntities(WarehouseLedgerExportAppService.ExportRequest req) {
        if ("ids".equals(req.scope())) {
            if (req.ids() == null || req.ids().isEmpty()) return List.of();
            return filterService.findAllByIds(req.ids());
        }
        if ("all_in_use".equals(req.scope())) {
            WarehouseFilterDTO f = new WarehouseFilterDTO(
                    null, null, List.of(WarehouseStatus.IN_USE), null, null, null, null,
                    null, null, null, null, null);
            return filterService.filterAll(f);
        }
        return filterService.filterAll(req.filter() != null ? req.filter() : null);
    }

    private Map<Long, String> loadUsernames(List<WarehouseEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        Set<Long> userIds = Stream.concat(
                entities.stream().map(WarehouseEntity::getCreatedBy),
                entities.stream().map(WarehouseEntity::getUpdatedBy))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> Objects.toString(u.getFullName(), ""),
                        (a, b) -> a));
    }

    private String saveZip(Long taskId, WarehouseExportZipBuilder.ZipBuildResult zip) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        String filename = "warehouse_ledger_" + taskId + "_" + ts + ".zip";
        Path target = dir.resolve(filename);
        Files.move(zip.zipFile(), target, StandardCopyOption.REPLACE_EXISTING);
        return target.toString();
    }

    private String buildSummary(int totalCount, WarehouseLedgerExportAppService.ExportRequest req,
                                long elapsedMs, WarehouseExportZipBuilder.ZipBuildResult zip) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("format", "ledger");
        map.put("totalCount", totalCount);
        map.put("scope", req.scope());
        map.put("sections", req.sections());
        map.put("elapsedMs", elapsedMs);
        map.put("xlsxBytes", zip.stats().xlsxBytes);
        map.put("zipBytes", zip.totalBytes());
        map.put("leaseContractCount", zip.stats().leaseContractCount);
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (com.fasterxml.jackson.core.JsonProcessingException | RuntimeException e) {
            return null;
        }
    }

    private Map<Long, List<WarehouseAttachmentEntity>> loadLeaseContractAttachments(List<WarehouseEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        List<Long> ids = entities.stream().map(WarehouseEntity::getId).toList();
        return attachmentRepo.findByWarehouseIdInAndType(ids, WarehouseAttachmentType.LEASE_CONTRACT).stream()
                .collect(Collectors.groupingBy(a -> a.getWarehouse().getId()));
    }
}
