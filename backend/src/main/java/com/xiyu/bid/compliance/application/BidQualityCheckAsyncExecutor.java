package com.xiyu.bid.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.common.util.StringUtils;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.policy.BidDocumentQualityEngine;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.LlmAdjudicator;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;
import com.xiyu.bid.compliance.domain.policy.TenderRequirements;
import com.xiyu.bid.compliance.entity.ComplianceCheckResult;
import com.xiyu.bid.compliance.repository.ComplianceCheckResultRepository;
import com.xiyu.bid.compliance.service.ComplianceTargetLoader;
import com.xiyu.bid.compliance.service.KnowledgeBaseOnDemandLoader;
import com.xiyu.bid.compliance.service.LlmVerdictParser;
import com.xiyu.bid.compliance.service.TenderDocumentLoader;
import com.xiyu.bid.compliance.service.TenderRequirementsExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 标书质量核查后台异步执行器.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BidQualityCheckAsyncExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /** LLM 裁决单项 max_tokens（裁决层只输出小 JSON，不需长响应）. */
    private static final int LLM_ADJUDICATION_MAX_TOKENS = 1024;

    /** 首次响应解析失败时的重试追加强约束（降低模型输出围栏/多余文字的概率）. */
    private static final String STRICT_JSON_RETRY_SUFFIX =
            "\n\n重申：仅输出一个 JSON 对象，不得包含任何其他文字、解释或代码块标记。";

    private final ComplianceCheckResultRepository resultRepository;
    private final ComplianceTargetLoader targetLoader;
    private final KnowledgeBaseOnDemandLoader kbLoader;
    private final TenderDocumentLoader tenderDocumentLoader;
    private final TenderRequirementsExtractor requirementsExtractor;
    private final AiProvider aiProvider;
    private final BidQualityCheckProgressUpdater progressUpdater;

    /**
     * 后台异步执行标书质量核查.
     *
     * @param resultId 检查结果记录ID
     * @param projectId 项目ID
     * @param taskId 任务UUID
     * @param force 是否强制跳过缓存重新核查
     */
    @Async
    public void executeAsync(final Long resultId, final Long projectId, final String taskId, final boolean force) {
        log.info("Starting async bid quality check task: {} for project: {}, force: {}", taskId, projectId, force);

        try {
            // Step 1: 解析文件 (progress: 20)
            progressUpdater.updateProgress(resultId, ComplianceCheckResult.TaskStatus.PARSING, 20, "正在加载并解析投标文件与招标文件...");

            DocumentContent bidDoc = targetLoader.loadBidDocument(projectId);
            if (bidDoc == null || bidDoc.fullText() == null || bidDoc.fullText().isBlank()) {
                log.warn("Bid document for project {} is empty or unparsable", projectId);
                progressUpdater.markFailed(resultId, "无法执行核查：投标文件解析失败，请确认文件可正常打开后重新上传");
                return;
            }

            DocumentContent tenderDoc = tenderDocumentLoader.loadTenderDocument(projectId);
            progressUpdater.updateFileMetadata(resultId, bidDoc.fileName(), bidDoc.fileHash(), tenderDoc.fileHash());

            // 缓存检查：若非强制刷新，检查当前文件的 SHA-256 哈希是否命中已有已完成记录
            if (!force && !bidDoc.fileHash().isBlank()) {
                Optional<ComplianceCheckResult> cachedOpt = resultRepository
                        .findTopByBidFileHashAndTenderFileHashAndStatusOrderByCheckedAtDesc(
                                bidDoc.fileHash(), tenderDoc.fileHash(), ComplianceCheckResult.TaskStatus.COMPLETED);

                if (cachedOpt.isPresent() && !cachedOpt.get().getId().equals(resultId)) {
                    ComplianceCheckResult cached = cachedOpt.get();
                    log.info("Hit SHA-256 cache for quality check, reusing result from task: {}", cached.getTaskId());
                    progressUpdater.applyCachedResult(resultId, cached);
                    return;
                }
            }

            // spec 049 第二步：招标要求 LLM 结构化提取（全部失败场景降级 missing 哨兵，不阻断任务）
            TenderRequirements requirements = requirementsExtractor.extract(tenderDoc);

            // Step 2: 知识库检索与 25 项规则核查 (progress: 60)
            progressUpdater.updateProgress(resultId, ComplianceCheckResult.TaskStatus.CHECKING, 60, "正在对照企业知识库并执行25项质量核查...");

            List<String> requiredCerts = requirements.mandatoryQualifications();
            // spec 049 第三步：四知识库真实检索；单库异常记录失败源，依赖项由引擎置 MANUAL 不阻断
            KnowledgeQueryOutcome kbOutcome = kbLoader != null
                    ? kbLoader.queryKnowledgeBases(requiredCerts)
                    : KnowledgeQueryOutcome.empty();

            // spec 049 第五步：LLM 混合裁决；AI 未启用降级 Noop，单项失败 LOW_CONFIDENCE，不阻断任务
            List<QualityCheckItemResult> itemResults = BidDocumentQualityEngine.evaluateAll(
                    bidDoc, tenderDoc, kbOutcome.matches(), requirements, kbOutcome.failedSources(),
                    new LlmAdjudicator(buildLlmPort()),
                    (progress, step) -> progressUpdater.updateProgress(resultId, ComplianceCheckResult.TaskStatus.CHECKING,
                            60 + progress * 30 / 100, "AI 语义裁决 " + progress + "%：" + step));

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

            ComplianceCheckResult.Status overallStatus;
            if (failed > 0) {
                overallStatus = ComplianceCheckResult.Status.NON_COMPLIANT;
            } else if (manual > 0) {
                overallStatus = ComplianceCheckResult.Status.PARTIAL_COMPLIANT;
            } else {
                overallStatus = ComplianceCheckResult.Status.COMPLIANT;
            }

            int riskScore = Math.min(100, failed * 20 + manual * 5);
            String checkDetailsJson = OBJECT_MAPPER.writeValueAsString(itemResults);

            progressUpdater.completeTask(resultId, overallStatus, riskScore, checkDetailsJson, passed, failed, manual, itemResults.size());

            log.info("Async bid quality check task completed: {}, status={}, passed={}, failed={}, manual={}",
                    taskId, overallStatus, passed, failed, manual);

        } catch (java.io.IOException | RuntimeException e) {
            log.error("Async bid quality check task failed: {}", taskId, e);
            progressUpdater.markFailed(resultId, e.getMessage() != null ? e.getMessage() : "核查过程中发生未知异常");
        }
    }

    /**
     * 重载兼容旧三参数调用（默认 force=false）.
     */
    public void executeAsync(final Long resultId, final Long projectId, final String taskId) {
        executeAsync(resultId, projectId, taskId, false);
    }

    /**
     * LLM 通道适配（副作用边界）：一切失败在本方法吸收为 {@link LlmVerdict.ChatOutcome} 值——
     * AI 未启用/未配置（IllegalStateException）→ Noop；其余调用异常 → Failure；
     * 响应 JSON 解析由 {@link LlmVerdictParser} 完成，非法时追加强约束重试 1 次，仍非法 → Invalid。纯核心无异常流.
     *
     * <p>包级可见：供同包单测直接验证重试契约（{@code BidQualityCheckAsyncExecutorTest}）.
     */
    LlmVerdict.LlmChatPort<LlmVerdict> buildLlmPort() {
        // 首次解析失败才重试（附严格 JSON 约束）；Noop/Failure 不重试，仍非法 → Invalid
        return prompt -> {
            Optional<LlmVerdict.ChatOutcome<LlmVerdict>> first = chatAndParse(prompt);
            if (first.isPresent()) {
                return first.get();
            }
            log.warn("LLM 裁决响应解析失败，追加严格 JSON 约束重试 1 次");
            return chatAndParse(prompt + STRICT_JSON_RETRY_SUFFIX)
                    .orElseGet(LlmVerdict::invalid);
        };
    }

    /**
     * 单次调用 + 解析：AI 未启用 → Noop；调用异常 → Failure；解析成功为终态；
     * 解析失败记录原始响应（截断）后返回 empty（可重试）.
     */
    private Optional<LlmVerdict.ChatOutcome<LlmVerdict>> chatAndParse(final String prompt) {
        String response;
        try {
            response = aiProvider.chatCompletion(prompt, LLM_ADJUDICATION_MAX_TOKENS);
        } catch (IllegalStateException e) {
            log.warn("AI 未启用或未配置，语义项降级为人工确认: {}", e.getMessage());
            return Optional.of(LlmVerdict.noop());
        } catch (RuntimeException e) {
            log.error("LLM 语义裁决调用失败", e);
            return Optional.of(LlmVerdict.failure(e.getMessage()));
        }
        Optional<LlmVerdict> parsed = LlmVerdictParser.parse(response);
        if (parsed.isEmpty()) {
            log.warn("LLM 裁决响应解析失败，原始响应(截断 500 字): {}",
                    response == null ? "(null)" : StringUtils.truncate(response, 500));
        }
        return parsed.map(LlmVerdict::success);
    }
}
