package com.xiyu.bid.tender.service;

import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.exception.TenderDuplicateException;
import com.xiyu.bid.tender.core.TenderDeduplicationPolicy;
import com.xiyu.bid.tender.crm.CachedCrmLookupService;
import com.xiyu.bid.tender.dto.TenderImportProgressDTO;
import com.xiyu.bid.tender.dto.TenderImportResultDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskDTO;
import com.xiyu.bid.tender.dto.TenderImportTaskError;
import com.xiyu.bid.tender.dto.TenderRequest;
import com.xiyu.bid.tender.entity.TenderImportTask;
import com.xiyu.bid.tender.repository.TenderImportTaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 标讯批量导入应用服务（异步编排层）。
 *
 * <p>职责：
 * <ul>
 *   <li>{@link #triggerImport}：同步阶段，校验文件 + 创建 PENDING 任务 + 读取 byte[] + 触发异步执行</li>
 *   <li>{@link #executeImportAsync}：异步阶段，Excel 解析 + 逐条入库 + 进度更新 + 状态机推进</li>
 *   <li>{@link #getProgress}：进度查询，校验任务归属后委托给 progressService</li>
 * </ul>
 *
 * <p>参考 {@link com.xiyu.bid.personnel.application.service.ImportPersonnelAppService} 的双方法模式：
 * 同步方法创建任务并返回 taskId，异步方法在新线程执行实际导入。
 *
 * <p><b>@Async 自调用问题</b>：Spring @Async 通过 AOP 代理实现，同类内方法互调（this.method()）
 * 不会触发代理。本类通过 {@code @Lazy @Autowired} 注入自身代理（{@link #self}），
 * {@code triggerImport} 内部通过 {@code self.executeImportAsync(...)} 调用，确保 @Async 生效。
 *
 * <p><b>异常兜底</b>：{@code executeImportAsync} 捕获 {@code IOException | RuntimeException | Error}
 * 全部异常（参考 CO-469 第四轮教训），调用 {@code failTaskWithThreeLayerFallback} 三层降级标记失败，
 * 避免任务静默卡死在 PROCESSING。
 *
 * @see com.xiyu.bid.personnel.application.service.ImportPersonnelAppService
 * @see TenderImportTaskStateService#failTaskWithThreeLayerFallback
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TenderImportAppService {

    private final TenderImportService tenderImportService;
    private final TenderImportTaskStateService taskStateService;
    private final TenderImportProgressService progressService;
    private final TenderImportTaskRepository taskRepository;
    private final TenderCommandService tenderCommandService;
    private final TenderMapper tenderMapper;
    private final TenderExcelParser excelParser;
    /** 031 R-007：导入批次内 CRM 反查缓存（同招标主体只调一次）。 */
    private final CachedCrmLookupService cachedCrmLookupService;

    /**
     * 自身代理（解决 @Async 自调用失效问题）。
     * <p>使用 @Lazy 避免循环依赖：Spring 在首次使用时才创建代理。
     */
    @Lazy
    @Autowired
    private TenderImportAppService self;

    /**
     * 触发标讯批量导入（同步阶段）。
     *
     * <p>同步阶段完成以下操作（预期 < 1s）：
     * <ol>
     *   <li>校验文件（非空 + 大小 + 后缀）</li>
     *   <li>生成 taskId (UUID)</li>
     *   <li>创建 PENDING 任务记录到 DB</li>
     *   <li>读取 MultipartFile 为 byte[]（绕开 Servlet 请求生命周期，CO-469 教训）</li>
     *   <li>通过自身代理触发 {@link #executeImportAsync} 异步执行</li>
     * </ol>
     *
     * @param file   上传的 Excel 文件（.xlsx，≤5MB）
     * @param userId 当前登录用户 ID
     * @return 任务创建响应（含 taskId，前端用于轮询进度）
     * @throws IllegalArgumentException 文件校验未通过
     */
    public TenderImportTaskDTO triggerImport(MultipartFile file, Long userId) {
        tenderImportService.validateFile(file);

        String taskId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        taskStateService.createTask(taskId, userId, fileName);

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            List<TenderImportTaskError> errors = List.of(new TenderImportTaskError(
                    0, "file", "读取文件失败: " + e.getMessage(), null));
            taskStateService.failTaskWithThreeLayerFallback(taskId, errors);
            throw new IllegalArgumentException("读取文件失败: " + e.getMessage(), e);
        }

        // 通过自身代理调用，确保 @Async 生效（避免自调用失效）
        self.executeImportAsync(taskId, fileBytes, userId);

        log.info("标讯导入任务已创建: taskId={} userId={} fileName={}", taskId, userId, fileName);
        return new TenderImportTaskDTO(taskId, "PENDING", 0, 0, 0, 0, "导入任务已创建，请稍后查询进度");
    }

    /**
     * 异步执行标讯导入（@Async 阶段）。
     *
     * <p>异步阶段完成以下操作：
     * <ol>
     *   <li>标记任务为 PROCESSING</li>
     *   <li>解析 Excel + 行级校验（委托 {@link TenderExcelParser#parseExcel}）</li>
     *   <li>校验未通过 → markFailed + 更新进度</li>
     *   <li>逐条 createTender（独立事务，部分成功）</li>
     *   <li>每条更新 Redis 进度（高频）</li>
     *   <li>根据结果 markCompleted / markPartialSuccess / markFailed</li>
     *   <li>完成后清 Redis（前端轮询 fallback 到 DB）</li>
     * </ol>
     *
     * <p><b>异常兜底</b>：catch (RuntimeException | Error)，调用
 * {@link TenderImportTaskStateService#failTaskWithThreeLayerFallback} 三层降级。
     *
     * @param taskId    任务 ID（UUID）
     * @param fileBytes Excel 文件字节（同步阶段已读取）
     * @param userId    当前登录用户 ID
     */
    @Async("tenderImportExecutor")
    public void executeImportAsync(String taskId, byte[] fileBytes, Long userId) {
        try {
            taskStateService.markProcessing(taskId);
            updateProgress(taskId, "PROCESSING", 0, 0, 0, 0, null);

            // Excel 解析 + 校验
            TenderExcelParser.ParsedExcel parsed = excelParser.parseExcel(fileBytes);
            int totalRows = parsed.totalRows();

            if (!parsed.errors().isEmpty()) {
                List<TenderImportTaskError> errors = convertErrors(parsed.errors());
                taskStateService.markFailed(taskId, errors);
                finalizeProgress(taskId, "FAILED", totalRows, 0, 0, errors.size(), errors);
                log.info("标讯导入校验未通过: taskId={} totalRows={} failureCount={}",
                        taskId, totalRows, errors.size());
                return;
            }

            // 逐条入库（独立事务，部分成功）
            // 031 R-007：批次内 CRM 缓存，同一招标主体只反查一次
            List<TenderImportTaskError> importErrors = new ArrayList<>();
            int successCount = 0;
            List<TenderRequest> rows = parsed.rows();

            cachedCrmLookupService.openBatch();
            try {
                for (int i = 0; i < rows.size(); i++) {
                    TenderRequest req = rows.get(i);
                    int displayRow = i + 2;
                    try {
                        tenderCommandService.createTender(tenderMapper.toDTO(req), userId);
                        successCount++;
                    } catch (TenderDuplicateException e) {
                        var existing = (e.getDuplicates() == null || e.getDuplicates().isEmpty())
                                ? null : e.getDuplicates().get(0);
                        importErrors.add(new TenderImportTaskError(displayRow, "duplicate",
                                TenderDeduplicationPolicy.formatImportDuplicateMessage(
                                        existing, req.getPurchaserName()),
                                req.getTitle()));
                    } catch (IllegalArgumentException e) {
                        importErrors.add(new TenderImportTaskError(displayRow, "row",
                                e.getMessage(), req.getTitle()));
                    } catch (RuntimeException e) {
                        importErrors.add(new TenderImportTaskError(displayRow, "row",
                                "导入失败：" + e.getMessage(), req.getTitle()));
                    }

                    int processed = i + 1;
                    updateProgress(taskId, "PROCESSING", totalRows, processed,
                            successCount, importErrors.size(), null);
                }
            } finally {
                cachedCrmLookupService.closeBatch();
            }

            // 标记终态
            int failureCount = importErrors.size();
            String finalStatus;
            if (failureCount == 0) {
                taskStateService.markCompleted(taskId, totalRows);
                finalStatus = "COMPLETED";
            } else if (successCount > 0) {
                taskStateService.markPartialSuccess(taskId, totalRows, successCount,
                        failureCount, importErrors);
                finalStatus = "PARTIAL_SUCCESS";
            } else {
                taskStateService.markFailed(taskId, importErrors);
                finalStatus = "FAILED";
            }

            finalizeProgress(taskId, finalStatus, totalRows, totalRows, successCount,
                    failureCount, failureCount > 0 ? importErrors : null);

            log.info("标讯导入完成: taskId={} totalRows={} success={} failure={}",
                    taskId, totalRows, successCount, failureCount);

        } catch (RuntimeException | Error e) {
            // CO-469 第四轮教训：catch 范围扩大到 Error，避免异步任务静默终止
            // 注：parseExcel 内部已将 IOException 包装为 IllegalArgumentException，故无需 catch IOException
            log.error("标讯导入任务执行失败: taskId={}", taskId, e);
            List<TenderImportTaskError> errors = List.of(new TenderImportTaskError(
                    0, "system",
                    e.getClass().getSimpleName() + ": " + e.getMessage(), null));
            taskStateService.failTaskWithThreeLayerFallback(taskId, errors);
        }
    }

    /**
     * 查询导入进度。
     *
     * <p>校验任务存在 + 任务归属（userId 匹配），然后委托给
     * {@link TenderImportProgressService#getProgress}（Redis 优先 + DB fallback）。
     *
     * @param taskId 任务 ID（UUID）
     * @param userId 当前登录用户 ID（用于归属校验）
     * @return 进度 DTO
     * @throws ResourceNotFoundException 任务不存在
     * @throws AccessDeniedException     任务不属于当前用户
     */
    public TenderImportProgressDTO getProgress(String taskId, Long userId) {
        TenderImportTask task = taskRepository.findByTaskId(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("TenderImportTask", taskId));
        if (!task.getUserId().equals(userId)) {
            throw new AccessDeniedException("无权查看此导入任务进度");
        }
        return progressService.getProgress(taskId);
    }

    /**
     * 更新 Redis 进度（处理中，高频调用）。
     */
    private void updateProgress(String taskId, String status, int totalRows, int processedRows,
                                int successCount, int failureCount,
                                List<TenderImportTaskError> errors) {
        int percent = totalRows > 0 ? (int) (100L * processedRows / totalRows) : 0;
        TenderImportProgressDTO progress = new TenderImportProgressDTO(
                taskId, status, totalRows, processedRows, successCount, failureCount,
                percent, errors, null, null);
        progressService.updateProgress(taskId, progress);
    }

    /**
     * 写终态到 Redis 后清缓存（前端下次轮询 fallback 到 DB）。
     * <p>先写终态再清 Redis：若 clearProgress 失败（Redis 不可用），Redis 中至少是正确终态。
     */
    private void finalizeProgress(String taskId, String status, int totalRows, int processedRows,
                                  int successCount, int failureCount,
                                  List<TenderImportTaskError> errors) {
        updateProgress(taskId, status, totalRows, processedRows, successCount, failureCount, errors);
        progressService.clearProgress(taskId);
    }

    /**
     * 将同步路径的 RowError 转换为异步任务错误明细。
     */
    private List<TenderImportTaskError> convertErrors(List<TenderImportResultDTO.RowError> rowErrors) {
        if (rowErrors == null || rowErrors.isEmpty()) {
            return List.of();
        }
        List<TenderImportTaskError> errors = new ArrayList<>(rowErrors.size());
        for (TenderImportResultDTO.RowError re : rowErrors) {
            errors.add(new TenderImportTaskError(re.row(), re.field(), re.message(), null));
        }
        return errors;
    }
}
