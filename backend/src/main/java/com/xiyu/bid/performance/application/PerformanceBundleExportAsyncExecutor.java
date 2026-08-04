package com.xiyu.bid.performance.application;

import com.xiyu.bid.performance.application.command.PerformanceSearchCriteria;
import com.xiyu.bid.performance.application.dto.PerformanceDTO;
import com.xiyu.bid.performance.application.mapper.PerformanceMapper;
import com.xiyu.bid.performance.domain.model.PerformanceAlertConfig;
import com.xiyu.bid.performance.domain.port.PerformanceAlertConfigRepository;
import com.xiyu.bid.performance.domain.port.PerformanceRepository;
import com.xiyu.bid.performance.infrastructure.PerformanceWordBundleBuilder;
import com.xiyu.bid.performance.infrastructure.persistence.entity.PerformanceExportTaskEntity;
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
import java.time.Duration;
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
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PerformanceBundleExportAsyncExecutor {

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Duration FILE_TTL = Duration.ofHours(24);

    /**
     * 单次导出最大业绩记录数。
     * <p>限制全量加载到内存的记录数，防止 OOM。
     * 超出时任务标记 FAILED 并提示用户缩小筛选范围或减少勾选数量。
     */
    public static final int MAX_EXPORT_RECORDS = 5000;

    /** 默认提醒配置（用于合同状态计算） */
    private static final PerformanceAlertConfig DEFAULT_CONFIG =
            new PerformanceAlertConfig(null, 180, 90, true);

    private final PerformanceBundleExportTaskStateService stateService;
    private final PerformanceRepository repository;
    private final PerformanceAlertConfigRepository alertConfigRepository;
    private final PerformanceMapper mapper;
    private final PerformanceWordBundleBuilder wordBundleBuilder;
    private final PerformanceBundleExportNotificationPublisher exportPublisher;

    /**
     * 导出文件落盘根目录。
     * <p>默认 {@code data/performance-bundle-exports}（持久化路径），
     * 生产环境应通过 {@code performance.bundle-export.root} 配置专用目录。
     * 禁止使用 {@code /tmp}：重启后文件丢失，导致下载 404。
     */
    @Value("${performance.bundle-export.root:data/performance-bundle-exports}")
    private String exportRoot;

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
            if (records.size() > MAX_EXPORT_RECORDS) {
                stateService.fail(taskId, "导出记录数 " + records.size() + " 超过上限 "
                        + MAX_EXPORT_RECORDS + "，请缩小筛选范围后重试");
                return;
            }
            doExport(taskId, records, attachmentTypes, filterSummary, startMs);
        } catch (RuntimeException e) {
            log.error("业绩合订本导出任务执行失败: taskId={}", taskId, e);
            stateService.fail(taskId, PerformanceBundleExportTaskStateService.truncate(e.getMessage(), 500));
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
            stateService.fail(taskId, PerformanceBundleExportTaskStateService.truncate(e.getMessage(), 500));
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
        Path wordFile = null;
        try {
            wordFile = Files.createTempFile("performance-bundle-", ".docx");
            long wordBytes;
            try (OutputStream out = Files.newOutputStream(wordFile)) {
                wordBundleBuilder.buildBundle(records, attachmentTypes, out);
            }
            wordBytes = Files.size(wordFile);

            String filePath = saveWord(taskId, wordFile);
            long elapsedMs = System.currentTimeMillis() - startMs;
            String resultSummary = exportPublisher.buildResultSummaryJson(
                    records.size(), wordBytes, attachmentTypes, elapsedMs, filterSummary);

            PerformanceExportTaskEntity task = stateService.complete(new PerformanceExportCompletion(
                    taskId, records.size(), filePath, resultSummary, FILE_TTL, startMs));
            exportPublisher.publish(task, records.size(), wordBytes, elapsedMs, filterSummary);
        } finally {
            // 删除临时文件（最终文件已 saveWord 复制到 exportRoot）
            if (wordFile != null) {
                try { Files.deleteIfExists(wordFile); } catch (IOException ignored) {}
            }
        }
    }

    private String saveWord(Long taskId, Path tempFile) throws IOException {
        Path dir = Paths.get(exportRoot);
        Files.createDirectories(dir);
        String ts = LocalDateTime.now().format(TS_FMT);
        Path dest = dir.resolve("performance_bundle_" + taskId + "_" + ts + ".docx");
        Files.copy(tempFile, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return dest.toString();
    }
}
