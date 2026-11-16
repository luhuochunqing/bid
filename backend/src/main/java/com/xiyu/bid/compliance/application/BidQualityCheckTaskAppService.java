// Input: 项目ID、任务UUID、force参数
// Output: 质量核查任务DTO与报告DTO，触发异步核查流水线
// Pos: compliance/application/ - 标书质量核查任务应用编排服务
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.application;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.dto.QualityCheckReportDTO;
import com.xiyu.bid.compliance.dto.QualityCheckTaskDTO;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.exception.BusinessException;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 标书质量核查任务应用编排服务.
 * 负责任务流转控制、毫秒级响应与报告组装.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BidQualityCheckTaskAppService {

    private final ComplianceCheckResultRepository resultRepository;
    private final ComplianceTargetLoader targetLoader;
    private final TenderDocumentLoader tenderDocumentLoader;
    private final BidQualityCheckAsyncExecutor asyncExecutor;

    /**
     * 触发标书质量核查异步任务（毫秒级响应）.
     *
     * @param projectId 项目ID
     * @param force 是否强制跳过缓存重新核查
     * @return 任务状态DTO
     */
    @Transactional
    public QualityCheckTaskDTO triggerQualityCheck(final Long projectId, final boolean force) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }

        // 1. 检查是否存在正在运行中的任务
        List<ComplianceCheckResult.TaskStatus> activeStatuses = List.of(
                ComplianceCheckResult.TaskStatus.INIT,
                ComplianceCheckResult.TaskStatus.PARSING,
                ComplianceCheckResult.TaskStatus.CHECKING
        );
        List<ComplianceCheckResult> running = resultRepository.findByProjectIdAndCheckTypeAndStatusIn(
                projectId, ComplianceCheckResult.CheckType.BID_DOCUMENT_QUALITY, activeStatuses);

        if (!running.isEmpty()) {
            ComplianceCheckResult existing = running.get(0);
            log.info("Found active running quality check task: {} for project: {}", existing.getTaskId(), projectId);
            return toTaskDTO(existing);
        }

        // 2. 同步前置元数据轻量校验（spec 049 第一步，contracts/bid-quality-api.md §1）：
        // 无 BID 类投标文件记录或无有效 fileUrl → 4xx，不创建任务；
        // 多册投标文件（价格/商务/技术）全部纳入本次核查
        List<ProjectDocument> bidDocs = targetLoader.findAllBidDocuments(projectId);
        if (bidDocs.isEmpty()) {
            throw new BusinessException(422, "无法执行核查，请先上传投标文件");
        }

        // 3. 创建新的异步任务记录（毫秒级入库）
        String taskId = UUID.randomUUID().toString().replace("-", "");
        ComplianceCheckResult taskEntity = ComplianceCheckResult.builder()
                .projectId(projectId)
                .taskId(taskId)
                .checkType(ComplianceCheckResult.CheckType.BID_DOCUMENT_QUALITY)
                .overallStatus(ComplianceCheckResult.Status.COMPLIANT)
                .status(ComplianceCheckResult.TaskStatus.INIT)
                .progress(0)
                .currentStep("初始化质量核查任务...")
                .riskScore(0)
                .bidFileName(joinBidFileNames(bidDocs))
                .checkedAt(LocalDateTime.now())
                .build();

        ComplianceCheckResult saved = resultRepository.save(taskEntity);

        // 4. 触发后台异步执行（大文件下载、瘦身、Sidecar解析、LLM核查等耗时动作全部在后台异步完成）
        // 在事务提交后（afterCommit）安全触发，确保后台独立事务进度更新器对记录立即可见
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncExecutor.executeAsync(saved.getId(), projectId, taskId, force);
                }
            });
        } else {
            asyncExecutor.executeAsync(saved.getId(), projectId, taskId, force);
        }

        log.info("Triggered new async quality check task: {} for project: {}, force: {}", taskId, projectId, force);
        return toTaskDTO(saved);
    }

    /**
     * 查询任务进度.
     */
    @Transactional(readOnly = true)
    public QualityCheckTaskDTO getTaskProgress(final String taskId) {
        if (taskId == null || taskId.isBlank()) {
            throw new IllegalArgumentException("Task ID cannot be null or empty");
        }
        ComplianceCheckResult result = resultRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found: " + taskId));
        return toTaskDTO(result);
    }

    /**
     * 查询项目最新的核查报告.
     */
    @Transactional(readOnly = true)
    public QualityCheckReportDTO getLatestQualityReport(final Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("Project ID cannot be null");
        }
        ComplianceCheckResult result = resultRepository
                .findTopByProjectIdAndCheckTypeOrderByCheckedAtDesc(
                        projectId, ComplianceCheckResult.CheckType.BID_DOCUMENT_QUALITY)
                .orElse(null);

        if (result == null) {
            return null;
        }
        return toReportDTO(result);
    }

    /**
     * 同步执行质量核查（向后兼容或测试使用）.
     */
    @Transactional
    public QualityCheckReportDTO executeSync(final Long projectId) {
        QualityCheckTaskDTO task = triggerQualityCheck(projectId, true);
        ComplianceCheckResult result = resultRepository.findByTaskId(task.getTaskId()).orElse(null);
        if (result != null && result.getStatus() != ComplianceCheckResult.TaskStatus.COMPLETED) {
            asyncExecutor.executeAsync(result.getId(), projectId, task.getTaskId(), true);
            result = resultRepository.findById(result.getId()).orElse(result);
        }
        return toReportDTO(result);
    }

    /**
     * 多册文件名按上传顺序顿号连接；空名经 {@link DocumentContent#displayFileName}
     * 单点兜底（与 Loader 解析名同一实现）；超长截断复用 {@link DocumentContent#capMergedFileName}.
     * 异步 updateFileMetadata 覆写 bid_file_name 时写入的是 merge 产出名（多册已经本函数同款截断；
     * 单册 identity 依赖 bid_file_name 与 project_document.name 列宽同为 255 天然安全）.
     */
    private static String joinBidFileNames(final List<ProjectDocument> bidDocs) {
        String joined = bidDocs.stream()
                .map(doc -> DocumentContent.displayFileName(doc.getName()))
                .collect(Collectors.joining(DocumentContent.MERGED_FILE_NAME_SEPARATOR));
        return DocumentContent.capMergedFileName(joined, bidDocs.size());
    }

    private QualityCheckTaskDTO toTaskDTO(final ComplianceCheckResult entity) {
        return QualityCheckTaskDTO.builder()
                .taskId(entity.getTaskId())
                .projectId(entity.getProjectId())
                .status(entity.getStatus())
                .progress(entity.getProgress())
                .currentStep(entity.getCurrentStep())
                .errorMessage(entity.getErrorMessage())
                .bidFileName(entity.getBidFileName())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    public static QualityCheckReportDTO toReportDTO(final ComplianceCheckResult entity) {
        List<QualityCheckItemResult> items = Collections.emptyList();
        boolean requiresRecheck = true;
        if (entity.getCheckDetails() != null && !entity.getCheckDetails().isBlank()) {
            try {
                // 按 itemCode 回填标准名称与检查项说明：历史快照（旧名称/无 description）展示口径对齐 PRD 2.3
                var snapshot = QualityReportSnapshot.read(entity.getCheckDetails());
                requiresRecheck = !QualityReportSnapshot.CURRENT_VERSION.equals(snapshot.version());
                items = snapshot.items().stream()
                        .map(QualityCheckItemResult::withCanonicalDefinition)
                        .toList();
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.warn("反序列化 25 项核查详情异常: {}", e.getMessage());
            }
        }
        return QualityCheckReportDTO.builder()
                .id(entity.getId())
                .taskId(entity.getTaskId())
                .projectId(entity.getProjectId())
                .status(entity.getStatus())
                .overallStatus(entity.getOverallStatus())
                .riskScore(entity.getRiskScore())
                .passedCount(entity.getPassedCount())
                .failedCount(entity.getFailedCount())
                .manualReviewCount(entity.getManualReviewCount())
                .totalCount(entity.getTotalCount())
                .bidFileName(entity.getBidFileName())
                .bidFileHash(entity.getBidFileHash())
                .tenderFileHash(entity.getTenderFileHash())
                .checkedAt(entity.getCheckedAt())
                .errorMessage(entity.getErrorMessage())
                .requiresRecheck(requiresRecheck)
                .items(items)
                .build();
    }
}
