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
import com.xiyu.bid.warehouse.infrastructure.WarehouseExportZipBuilder;
import com.xiyu.bid.warehouse.infrastructure.WarehouseWordBundleBuilder;
import com.xiyu.bid.warehouse.service.WarehouseFilterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 仓库台账导出异步执行器 — 承载 @Async 方法，从 WarehouseExportAppService 提取。
 *
 * <p>根因（CO-582 回归）：原 AppService.export() 在 @Transactional 方法内直接调用
 * this.executeExportAsync()，Spring AOP 代理不拦截同类内部方法调用（self-invocation），
 * 导致 @Async("warehouseExportExecutor") 注解静默失效。Word 合订本生成（含 PDF 渲染）
 * 在 HTTP 请求线程同步执行，超过前端 axios 30 秒超时，前端显示"创建导出任务失败"。
 *
 * <p>修复：提取到独立 Spring Bean，通过依赖注入调用，使 @Async 代理生效。
 * 这是 Spring @Async self-invocation 失效的标准修复模式。
 *
 * <p>状态机委托 {@link WarehouseExportTaskStateService}（对标 TenderImportTaskStateService），
 * 避免重复实现 markProcessing/complete/fail。
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WarehouseExportAsyncExecutor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofHours(24);

    private final WarehouseExportTaskStateService stateService;
    private final WarehouseFilterService filterService;
    private final WarehouseExcelWriter excelWriter;
    private final WarehouseAttachmentRepository attachmentRepo;
    private final WarehouseExportZipBuilder zipBuilder;
    private final WarehouseWordBundleBuilder wordBundleBuilder;
    private final WarehouseExportNotificationPublisher exportPublisher;
    private final UserRepository userRepository;

    @Value("${warehouse.export.root:/tmp/warehouse-exports}")
    private String exportRoot;

    /**
     * 按 filter 模式异步执行仓库导出。
     */
    @Async("warehouseExportExecutor")
    public void executeExport(Long taskId, WarehouseFilterDTO filterDTO, Long operatorId,
                              String operatorUsername, WarehouseAttachmentExportScope attachmentScope,
                              Set<WarehouseAttachmentOrganizationForm> attachmentForms,
                              long startMs) {
        try {
            stateService.markProcessing(taskId);
            List<WarehouseEntity> entities = filterService.filterAll(filterDTO);
            doExport(taskId, entities, filterDTO, "当前筛选", attachmentScope, attachmentForms, startMs);
        } catch (RuntimeException e) {
            log.error("仓库台账导出任务执行失败: taskId={}", taskId, e);
            stateService.fail(taskId, WarehouseExportTaskStateService.truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库台账导出文件IO异常: taskId={}", taskId, e);
            stateService.fail(taskId, "文件写入失败: " + e.getMessage());
        } catch (Error e) {
            // CO-469 第四轮教训：必须 catch Error，否则线程池线程被杀死，任务永远卡 PENDING/PROCESSING
            log.error("仓库导出遭遇 Error，尝试标记失败: taskId={}", taskId, e);
            stateService.fail(taskId, "系统资源异常: " + e.getClass().getSimpleName());
            throw e;
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
            stateService.markProcessing(taskId);
            List<WarehouseEntity> entities = filterService.findAllByIds(ids);
            doExport(taskId, entities, null, "勾选模式", attachmentScope, attachmentForms, startMs);
        } catch (RuntimeException e) {
            log.error("仓库按ID批量导出任务执行失败: taskId={}", taskId, e);
            stateService.fail(taskId, WarehouseExportTaskStateService.truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("仓库按ID批量导出文件IO异常: taskId={}", taskId, e);
            stateService.fail(taskId, "文件写入失败: " + e.getMessage());
        } catch (Error e) {
            log.error("仓库按ID导出遭遇 Error，尝试标记失败: taskId={}", taskId, e);
            stateService.fail(taskId, "系统资源异常: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    private void doExport(Long taskId, List<WarehouseEntity> entities, WarehouseFilterDTO filterDTO,
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
        Path wordFile = null;
        if (attachmentForms != null && attachmentForms.contains(WarehouseAttachmentOrganizationForm.WORD_COMBINED)) {
            try {
                wordFile = Files.createTempFile("warehouse-word-bundle-", ".docx");
                try (OutputStream out = Files.newOutputStream(wordFile)) {
                    wordBundleBuilder.buildBundle(entities, filteredAttachments, out);
                }
            } catch (RuntimeException e) {
                log.warn("Word 合订本生成失败，降级为仅附件目录+台账: taskId={}", taskId, e);
                if (wordFile != null) {
                    try { Files.deleteIfExists(wordFile); } catch (IOException ignored) {}
                }
                wordFile = null;
            }
        }
        WarehouseExportZipBuilder.ZipBuildResult zip = zipBuilder.buildZip(
                xlsxBytes, entities, filteredAttachments, wordFile, attachmentForms);
        try {
            String filePath = saveZip(taskId, zip);
            long elapsedMs = System.currentTimeMillis() - startMs;
            String resultSummary = exportPublisher.buildResultSummaryJson(
                    entities.size(), zip, filterDTO, elapsedMs, attachmentScope);
            WarehouseExportTaskEntity task = stateService.complete(new ExportCompletion(
                    taskId, entities.size(), filePath, resultSummary, FILE_TTL, startMs));
            // 通知发布是附加操作，失败不应影响主流程；放在事务外执行
            exportPublisher.publish(task, entities.size(), zip, filterDTO, elapsedMs, TS_FMT, attachmentScope);
        } finally {
            try { Files.deleteIfExists(zip.zipFile()); } catch (IOException ignored) { log.debug("Failed to delete zip file", ignored); }
            if (wordFile != null) {
                try { Files.deleteIfExists(wordFile); } catch (IOException ignored) { log.debug("Failed to delete word file", ignored); }
            }
        }
    }

    private Map<Long, List<WarehouseAttachmentEntity>> loadAttachments(List<WarehouseEntity> entities) {
        if (entities.isEmpty()) return Map.of();
        List<Long> ids = entities.stream().map(WarehouseEntity::getId).toList();
        return attachmentRepo.findByWarehouseIdIn(ids).stream()
                .collect(Collectors.groupingBy(a -> a.getWarehouse().getId()));
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
                        (a, b) -> a)); // CO-027: merge function 防止 Duplicate key 异常
    }

    private String saveZip(Long taskId, WarehouseExportZipBuilder.ZipBuildResult zip) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        Path dest = dir.resolve("warehouse_export_" + taskId + "_" + ts + ".zip");
        // 原子移动（同分区），避免 copy + delete 两次磁盘 IO
        Files.move(zip.zipFile(), dest, StandardCopyOption.REPLACE_EXISTING);
        return dest.toString();
    }
}
