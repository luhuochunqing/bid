package com.xiyu.bid.warehouse.application;

import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportPolicy;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentExportScope;
import com.xiyu.bid.warehouse.domain.WarehouseAttachmentOrganizationForm;
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
import com.xiyu.bid.warehouse.infrastructure.WarehouseWordBundleBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 仓库台账导出异步执行器 — 承载 @Async 方法，从 WarehouseExportAppService 提取。
 *
 * 根因（CO-582 回归）：原 AppService.export() 在 @Transactional 方法内直接调用
 * this.executeExportAsync()，Spring AOP 代理不拦截同类内部方法调用（self-invocation），
 * 导致 @Async("warehouseExportExecutor") 注解静默失效。Word 合订本生成（含 PDF 渲染）
 * 在 HTTP 请求线程同步执行，超过前端 axios 30 秒超时，前端显示"创建导出任务失败"。
 *
 * 修复：提取到独立 Spring Bean，通过依赖注入调用，使 @Async 代理生效。
 * 这是 Spring @Async self-invocation 失效的标准修复模式。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportAsyncExecutor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofHours(24);

    private final WarehouseExportTaskRepository exportTaskRepo;
    private final WarehouseFilterService filterService;
    private final WarehouseExcelWriter excelWriter;
    private final WarehouseAttachmentRepository attachmentRepo;
    private final WarehouseExportZipBuilder zipBuilder;
    private final WarehouseWordBundleBuilder wordBundleBuilder;
    private final WarehouseExportNotificationPublisher exportPublisher;
    private final UserRepository userRepository;

    @Value("${warehouse.export.root:/tmp/warehouse-exports}")
    private String exportRoot;

    /** 包级可见 setter，用于单元测试注入临时目录。 */
    void setExportRoot(String exportRoot) {
        this.exportRoot = exportRoot;
    }

    /**
     * 按 filter 模式异步执行仓库导出。
     */
    @Async("warehouseExportExecutor")
    public void executeExport(Long taskId, WarehouseFilterDTO filterDTO, Long operatorId,
                              String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                              Set<WarehouseAttachmentOrganizationForm> attachmentForms,
                              long startMs) {
        try {
            markProcessing(taskId);
            List<WarehouseEntity> entities = filterService.filterAll(filterDTO);
            doExport(taskId, operatorId, operatorUsername, entities, filterDTO, "当前筛选",
                    attachmentScope, attachmentForms, startMs);
        } catch (RuntimeException e) {
            log.error("仓库台账导出任务执行失败: taskId={}", taskId, e);
            failTask(taskId, truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库台账导出文件IO异常: taskId={}", taskId, e);
            failTask(taskId, "文件写入失败: " + e.getMessage());
        }
    }

    /**
     * 按 ids 模式异步执行仓库导出。
     */
    @Async("warehouseExportExecutor")
    public void executeExportByIds(Long taskId, List<Long> ids, Long operatorId,
                                   String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                                   Set<WarehouseAttachmentOrganizationForm> attachmentForms,
                                   long startMs) {
        try {
            markProcessing(taskId);
            List<WarehouseEntity> entities = filterService.findAllByIds(ids);
            doExport(taskId, operatorId, operatorUsername, entities, null, "勾选模式",
                    attachmentScope, attachmentForms, startMs);
        } catch (RuntimeException e) {
            log.error("仓库按ID批量导出任务执行失败: taskId={}", taskId, e);
            failTask(taskId, truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库按ID批量导出文件IO异常: taskId={}", taskId, e);
            failTask(taskId, "文件写入失败: " + e.getMessage());
        }
    }

    private void doExport(Long taskId, Long operatorId, String operatorUsername,
                          List<WarehouseEntity> entities, WarehouseFilterDTO filterDTO,
                          String scope, WarehouseAttachmentExportScope attachmentScope,
                          Set<WarehouseAttachmentOrganizationForm> attachmentForms,
                          long startMs) throws IOException {
        Map<Long, List<WarehouseAttachmentEntity>> attachmentsByWhId = loadAttachments(entities);
        Map<Long, List<WarehouseAttachmentEntity>> filteredAttachments = WarehouseAttachmentExportPolicy.filter(
                attachmentScope, attachmentsByWhId);
        Map<Long, String> usernameById = loadUsernames(entities);
        List<String[]> rows = WarehouseExportPolicy.buildRows(entities, filteredAttachments, usernameById);
        byte[] xlsxBytes = excelWriter.write(WarehouseExportPolicy.HEADERS, rows);
        // 需求 §4：Word 合订本生成失败不影响附件目录导出，独立 try-catch 降级为 null
        byte[] wordBytes = null;
        if (attachmentForms != null && attachmentForms.contains(WarehouseAttachmentOrganizationForm.WORD_COMBINED)) {
            try { wordBytes = wordBundleBuilder.buildBundle(entities, filteredAttachments); }
            catch (RuntimeException e) { log.warn("Word 合订本生成失败，降级为仅附件目录+台账: taskId={}", taskId, e); }
        }
        WarehouseExportZipBuilder.ZipBuildResult zip = zipBuilder.buildZip(
                xlsxBytes, entities, filteredAttachments, wordBytes, attachmentForms);
        try {
            String filePath = saveZip(taskId, zip);
            completeTask(taskId, operatorId, operatorUsername, entities, filePath, zip, filterDTO, scope, attachmentScope, startMs);
        } finally {
            try { Files.deleteIfExists(zip.zipFile()); } catch (IOException ignored) { log.debug("Failed to delete zip file", ignored); }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markProcessing(Long taskId) {
        exportTaskRepo.findById(taskId).ifPresent(task -> {
            task.setStatus(ExportStatus.PROCESSING);
            exportTaskRepo.save(task);
        });
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failTask(Long taskId, String reason) {
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

    private Map<Long, List<WarehouseAttachmentEntity>> loadAttachments(List<WarehouseEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        List<Long> ids = entities.stream().map(WarehouseEntity::getId).toList();
        return attachmentRepo.findByWarehouseIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getWarehouse().getId()));
    }

    private Map<Long, String> loadUsernames(List<WarehouseEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        Set<Long> userIds = entities.stream()
                .map(WarehouseEntity::getCreatedBy).filter(id -> id != null)
                .collect(Collectors.toSet());
        userIds.addAll(entities.stream()
                .map(WarehouseEntity::getUpdatedBy).filter(id -> id != null)
                .toList());
        if (userIds.isEmpty()) return Map.of();
        return userRepository.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> nvl(u.getFullName()),
                        (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常
    }

    private static String nvl(String s) { return s != null ? s : ""; }

    private String saveZip(Long taskId, WarehouseExportZipBuilder.ZipBuildResult zip) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        Path dest = dir.resolve("warehouse_export_" + taskId + "_" + ts + ".zip");
        Files.copy(zip.zipFile(), dest);
        return dest.toString();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeTask(Long taskId, Long operatorId, String operatorUsername,
                             List<WarehouseEntity> entities, String filePath,
                             WarehouseExportZipBuilder.ZipBuildResult zip,
                             WarehouseFilterDTO filterDTO, String scope,
                             WarehouseAttachmentExportScope attachmentScope, long startMs) {
        long elapsedMs = System.currentTimeMillis() - startMs;
        LocalDateTime now = LocalDateTime.now();
        WarehouseExportTaskEntity task = exportTaskRepo.findById(taskId).orElseThrow();
        task.setStatus(ExportStatus.COMPLETED);
        task.setTotalCount(entities.size());
        task.setStoredFilePath(filePath);
        task.setDownloadUrl("/api/knowledge/warehouses/export/tasks/" + taskId + "/download");
        task.setExpiresAt(now.plus(FILE_TTL));
        task.setCompletedAt(now);
        task.setResultSummary(exportPublisher.buildResultSummaryJson(entities.size(), zip, filterDTO, elapsedMs, attachmentScope));
        exportTaskRepo.save(task);
        exportPublisher.publish(task, entities.size(), zip, filterDTO, elapsedMs, TS_FMT, attachmentScope);
    }
}
