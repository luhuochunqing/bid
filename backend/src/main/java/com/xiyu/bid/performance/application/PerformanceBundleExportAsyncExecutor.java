package com.xiyu.bid.performance.application;

import com.xiyu.bid.common.application.ExportTaskCompletion;
import com.xiyu.bid.common.util.StringUtils;
import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.config.PerformanceBundleExportProperties;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.performance.infrastructure.PerformanceWordBundleBuilder;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;

/**
 * 业绩合订本导出异步执行器。
 *
 * <p>从 {@link PerformanceBundleExportAppService} 提取 @Async 方法到独立 Bean，
 * 避免 Spring AOP self-invocation 导致 @Async 注解失效（参考仓库模块 CO-582 修复）。
 *
 * <p>状态机委托 {@link PerformanceBundleExportTaskStateService}，
 * 文档构建委托 {@link PerformanceWordBundleBuilder}。
 *
 * <p>设计评估修复（CO-602）：
 * <ul>
 *   <li>D4-1：MAX_EXPORT_RECORDS 由 Properties 注入，默认 2000（原 5000 过高）</li>
 *   <li>D4-3：直接写到目标目录的 .tmp 文件，成功后原子 rename，避免额外复制</li>
 *   <li>D5-1：FILE_TTL 由 Properties 注入，默认 7 天（原 24h 过短）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceBundleExportAsyncExecutor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /** 默认提醒配置（用于合同状态计算） */
    private static final PerformanceAlertConfig DEFAULT_CONFIG =
            new PerformanceAlertConfig(null, 180, 90, true);

    private final PerformanceBundleExportTaskStateService stateService;
    private final PerformanceRepository repository;
    private final PerformanceAlertConfigRepository alertConfigRepository;
    private final PerformanceMapper mapper;
    private final PerformanceWordBundleBuilder wordBundleBuilder;
    private final PerformanceBundleExportNotificationPublisher exportPublisher;
    private final PerformanceBundleExportProperties properties;

    /**
     * 按 filter 模式异步执行业绩合订本导出。
     */
    @Async("performanceBundleExportExecutor")
    public void executeExport(Long taskId, PerformanceSearchCriteria criteria,
                              Set<String> attachmentTypes, Long operatorId,
                              String filterSummary, long startMs) {
        try {
            stateService.markProcessing(taskId);
            PerformanceAlertConfig config = alertConfigRepository.findActive().orElse(DEFAULT_CONFIG);
            PerformanceSearchCriteria effective = criteria != null ? criteria : PerformanceSearchCriteria.empty();
            List<PerformanceDTO> records = repository.findAll(effective, config).stream()
                    .map(mapper::toDTO)
                    .toList();
            int maxRecords = properties.getMaxExportRecords();
            if (records.size() > maxRecords) {
                stateService.fail(taskId, "导出记录数 " + records.size() + " 超过上限 "
                        + maxRecords + "，请缩小筛选范围后重试");
                return;
            }
            doExport(taskId, records, attachmentTypes, filterSummary, startMs);
        } catch (RuntimeException e) {
            log.error("业绩合订本导出任务执行失败: taskId={}", taskId, e);
            stateService.fail(taskId, StringUtils.truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("业绩合订本导出文件IO异常: taskId={}", taskId, e);
            stateService.fail(taskId, "文件写入失败: " + e.getMessage());
        } catch (Error e) {
            log.error("业绩合订本导出遭遇 Error，尝试标记失败: taskId={}", taskId, e);
            stateService.fail(taskId, "系统资源异常: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    /**
     * 按 ids 模式异步执行业绩合订本导出。
     */
    @Async("performanceBundleExportExecutor")
    public void executeExportByIds(Long taskId, List<Long> ids,
                                    Set<String> attachmentTypes, Long operatorId,
                                    String filterSummary, long startMs) {
        try {
            stateService.markProcessing(taskId);
            // 批量查询避免 N+1（原循环 findById 在 100 条业绩时产生 100 次 DB 查询）
            List<PerformanceDTO> records = repository.findAllById(ids).stream()
                    .map(mapper::toDTO)
                    .filter(r -> r != null)
                    .toList();
            doExport(taskId, records, attachmentTypes, filterSummary, startMs);
        } catch (RuntimeException e) {
            log.error("业绩按ID批量合订本导出任务执行失败: taskId={}", taskId, e);
            stateService.fail(taskId, StringUtils.truncate(e.getMessage(), 500));
        } catch (IOException e) {
            log.error("业绩按ID合订本导出文件IO异常: taskId={}", taskId, e);
            stateService.fail(taskId, "文件写入失败: " + e.getMessage());
        } catch (Error e) {
            log.error("业绩按ID合订本导出遭遇 Error，尝试标记失败: taskId={}", taskId, e);
            stateService.fail(taskId, "系统资源异常: " + e.getClass().getSimpleName());
            throw e;
        }
    }

    private void doExport(Long taskId, List<PerformanceDTO> records,
                          Set<String> attachmentTypes, String filterSummary,
                          long startMs) throws IOException {
        // D4-3 修复：直接写到目标目录的 .tmp 文件，成功后原子 rename，避免额外复制
        Path dir = properties.resolveAbsoluteRoot();
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        Path tempDest = dir.resolve("performance_bundle_" + taskId + "_" + ts + ".docx.tmp");
        try {
            long wordBytes;
            try (OutputStream out = Files.newOutputStream(tempDest)) {
                wordBundleBuilder.buildBundle(records, attachmentTypes, out);
            }
            wordBytes = Files.size(tempDest);

            Path finalDest = tempDest.resolveSibling(
                    tempDest.getFileName().toString().replace(".tmp", ""));
            Files.move(tempDest, finalDest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            String filePath = finalDest.toString();

            long elapsedMs = System.currentTimeMillis() - startMs;
            String resultSummary = exportPublisher.buildResultSummaryJson(
                    records.size(), wordBytes, attachmentTypes, elapsedMs, filterSummary);

            PerformanceExportTaskEntity task = stateService.complete(new ExportTaskCompletion(
                    taskId, records.size(), filePath, resultSummary, properties.getFileTtl(), startMs));
            exportPublisher.publish(task, records.size(), wordBytes, elapsedMs, filterSummary);
        } finally {
            // 异常时清理 .tmp 文件（成功时已被 move 走）
            try { Files.deleteIfExists(tempDest); } catch (IOException ignored) {}
        }
    }
}
