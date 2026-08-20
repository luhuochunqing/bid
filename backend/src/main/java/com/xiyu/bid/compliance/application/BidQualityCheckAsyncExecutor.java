package com.xiyu.bid.compliance.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import com.xiyu.bid.compliance.domain.policy.BidDocumentQualityEngine;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.KnowledgeBaseOnDemandLoader;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.compliance.service.TenderRequirementsExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 标书质量核查后台异步执行器.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BidQualityCheckAsyncExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final ComplianceCheckResultRepository resultRepository;
    private final ComplianceTargetLoader targetLoader;
    private final KnowledgeBaseOnDemandLoader kbLoader;
    private final TenderDocumentLoader tenderDocumentLoader;
    private final TenderRequirementsExtractor requirementsExtractor;

    /**
     * 后台异步执行标书质量核查.
     */
    @Async
    @Transactional
    public void executeAsync(final Long resultId, final Long projectId, final String taskId) {
        log.info("Starting async bid quality check task: {} for project: {}", taskId, projectId);

        ComplianceCheckResult checkResult = resultRepository.findById(resultId).orElse(null);
        if (checkResult == null) {
            log.warn("ComplianceCheckResult not found for id: {}", resultId);
            return;
        }

        try {
            // Step 1: 解析文件 (progress: 20)
            updateTaskStep(checkResult, ComplianceCheckResult.TaskStatus.PARSING, 20, "正在加载并解析投标文件与招标文件...");

            DocumentContent bidDoc = targetLoader.loadBidDocument(projectId);
            DocumentContent tenderDoc = tenderDocumentLoader.loadTenderDocument(projectId);
            // spec 049 第二步：招标要求 LLM 结构化提取（全部失败场景降级 missing 哨兵，不阻断任务）
            TenderRequirements requirements = requirementsExtractor.extract(tenderDoc);

            checkResult.setBidFileName(bidDoc.fileName());
            checkResult.setBidFileHash(bidDoc.fileHash());
            checkResult.setTenderFileHash(tenderDoc.fileHash());

            // Step 2: 知识库检索与 25 项规则核查 (progress: 60)
            updateTaskStep(checkResult, ComplianceCheckResult.TaskStatus.CHECKING, 60, "正在对照企业知识库并执行25项质量核查...");

            List<String> requiredCerts = requirements.mandatoryQualifications();
            // spec 049 第三步：四知识库真实检索；单库异常记录失败源，依赖项由引擎置 MANUAL 不阻断
            KnowledgeQueryOutcome kbOutcome = kbLoader != null
                    ? kbLoader.queryKnowledgeBases(requiredCerts)
                    : KnowledgeQueryOutcome.empty();
            List<QualityEvidence> kbEvidences = kbOutcome.matches().stream()
                    .map(QualityEvidence::fromKnowledgeMatch)
                    .toList();

            List<QualityCheckItemResult> itemResults = BidDocumentQualityEngine.evaluateAll(
                    bidDoc, tenderDoc, kbEvidences, requirements, kbOutcome.failedSources());

            // Step 3: 汇总指标并持久化 (progress: 100)
            int passed = 0;
            int failed = 0;
            int manual = 0;
            for (QualityCheckItemResult r : itemResults) {
                if (r.status() == QualityCheckStatus.PASS) {
                    passed++;
                } else if (r.status() == QualityCheckStatus.FAIL) {
                    failed++;
                } else {
                    manual++;
                }
            }

            checkResult.setPassedCount(passed);
            checkResult.setFailedCount(failed);
            checkResult.setManualReviewCount(manual);
            checkResult.setTotalCount(itemResults.size());

            // 计算整体合规状态
            if (failed > 0) {
                checkResult.setOverallStatus(ComplianceCheckResult.Status.NON_COMPLIANT);
            } else if (manual > 0) {
                checkResult.setOverallStatus(ComplianceCheckResult.Status.PARTIAL_COMPLIANT);
            } else {
                checkResult.setOverallStatus(ComplianceCheckResult.Status.COMPLIANT);
            }

            int riskScore = Math.min(100, failed * 20 + manual * 5);
            checkResult.setRiskScore(riskScore);
            checkResult.setCheckDetails(OBJECT_MAPPER.writeValueAsString(itemResults));
            checkResult.setCheckedAt(LocalDateTime.now());

            updateTaskStep(checkResult, ComplianceCheckResult.TaskStatus.COMPLETED, 100, "核查完成");
            resultRepository.save(checkResult);

            log.info("Async bid quality check task completed: {}, status={}, passed={}, failed={}, manual={}",
                    taskId, checkResult.getOverallStatus(), passed, failed, manual);

        } catch (java.io.IOException | RuntimeException e) {
            log.error("Async bid quality check task failed: {}", taskId, e);
            checkResult.setStatus(ComplianceCheckResult.TaskStatus.FAILED);
            checkResult.setProgress(100);
            checkResult.setCurrentStep("核查失败");
            checkResult.setErrorMessage(e.getMessage() != null ? e.getMessage() : "核查过程中发生未知异常");
            resultRepository.save(checkResult);
        }
    }

    private void updateTaskStep(
            final ComplianceCheckResult checkResult,
            final ComplianceCheckResult.TaskStatus status,
            final int progress,
            final String currentStep) {
        checkResult.setStatus(status);
        checkResult.setProgress(progress);
        checkResult.setCurrentStep(currentStep);
        resultRepository.save(checkResult);
    }
}
