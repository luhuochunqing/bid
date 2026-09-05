// Input: 检查结果ID、项目ID、任务UUID、force参数
// Output: 异步执行25项质量核查，进度持久化到DB
// Pos: compliance/application/ - 标书质量核查后台异步执行器
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.compliance.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.ai.client.AiProvider;
import com.xiyu.bid.common.util.StringUtils;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualificationDetail;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.policy.BidDocumentQualityEngine;
import com.xiyu.bid.compliance.domain.policy.DeterministicDepositExtractor;
import com.xiyu.bid.compliance.domain.policy.DeterministicQualificationExtractor;
import com.xiyu.bid.compliance.domain.policy.KnowledgeQueryOutcome;
import com.xiyu.bid.compliance.domain.policy.LlmAdjudicator;
import com.xiyu.bid.compliance.domain.policy.LlmVerdict;
import com.xiyu.bid.compliance.domain.policy.QualificationMatchPolicy;
import com.xiyu.bid.compliance.domain.policy.SemanticRulesLane;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 标书质量核查后台异步执行器.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BidQualityCheckAsyncExecutor {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * LLM 裁决单项 max_tokens（正文约 200 token，余量给 thinking——thinking 系
     * 模型的内部推理 token 同样计入 max_tokens，给小了正文会被截断致 JSON 不配平，
     * 1024 时曾致 7/7 语义项转人工，见 lessons #201）.
     */
    private static final int LLM_ADJUDICATION_MAX_TOKENS = 4096;

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
    @Async("aiTaskExecutor")
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

            // 双轨融合：纯核心确定性规则提取（100% 规则锚定招标文件资格与资质条款，杜绝漏识别）
            List<String> deterministicQuals = DeterministicQualificationExtractor.extract(tenderDoc.fullText());
            if (!deterministicQuals.isEmpty()) {
                requirements = requirements.withMergedQualifications(deterministicQuals);
            }

            // 双轨交叉融合：结合确定性规则与 LLM 提取，硬事实金额优先（双有数时以确定性原文为准），消除 LLM 幻觉风险
            TenderRequirements.DepositRequirement detDeposit = DeterministicDepositExtractor.extract(tenderDoc.fullText());
            if (detDeposit != null) {
                TenderRequirements.DepositRequirement fused = TenderRequirements.DepositRequirement.fuseWithDeterministic(
                        requirements.depositRequirement(), detDeposit);
                requirements = requirements.withDepositRequirement(fused);
            }

            // Step 2: 知识库检索与 25 项规则核查 (progress: 60)
            progressUpdater.updateProgress(resultId, ComplianceCheckResult.TaskStatus.CHECKING, 60, "正在对照企业知识库并执行25项质量核查...");

            List<String> requiredCerts = requirements.mandatoryQualifications();
            // spec 049 第三步：四知识库真实检索；单库异常记录失败源，依赖项由引擎置 MANUAL 不阻断
            KnowledgeQueryOutcome kbOutcome = kbLoader != null
                    ? kbLoader.queryKnowledgeBases(requiredCerts)
                    : KnowledgeQueryOutcome.empty();

            // spec 049 第五步：LLM 混合裁决；AI 未启用降级 Noop，单项失败 LOW_CONFIDENCE，不阻断任务
            AtomicInteger parseFailureCounter = new AtomicInteger();
            List<QualityCheckItemResult> itemResults = BidDocumentQualityEngine.evaluateAll(
                    bidDoc, tenderDoc, kbOutcome.matches(), requirements, kbOutcome.failedSources(),
                    new LlmAdjudicator(buildLlmPort(parseFailureCounter)),
                    (progress, step) -> progressUpdater.updateProgress(resultId, ComplianceCheckResult.TaskStatus.CHECKING,
                            60 + progress * 30 / 100, "AI 语义裁决 " + progress + "%：" + step));

            // 组装资质比对结构化清单（招标要求 vs 我司证书 vs 标书页码）
            List<QualificationDetail> qualDetails = QualificationMatchPolicy.buildDetails(
                    requirements.mandatoryQualifications(),
                    kbLoader != null ? kbLoader.findAllQualifications() : List.of(),
                    bidDoc,
                    LocalDate.now());
            if (!qualDetails.isEmpty()) {
                itemResults = itemResults.stream()
                        .map(r -> BidQualityCheckItem.QUALIFICATION_MATCH.getCode().equals(r.itemCode())
                                ? r.withDetails(qualDetails)
                                : r)
                        .toList();
            }

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

            int parseFailures = parseFailureCounter.get();
            if (parseFailures >= PARSE_FAILURE_ALERT_THRESHOLD) {
                log.error("LLM 裁决解析失败聚告警: 任务 {} 有 {}/{} 个语义项解析失败——大概率是模型/网关配置问题"
                        + "（如 thinking 系模型 max_tokens 预算不足，见 lessons #201），而非标书内容问题，请检查 AI 模型配置",
                        taskId, parseFailures, SemanticRulesLane.SEMANTIC_ITEMS.size());
            }

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
     * 优先调用结构化输出通道 {@code aiProvider.chatCompletionJsonObject}；
     * AI 未启用/未配置（IllegalStateException）→ Noop；其余调用异常 → Failure；
     * 响应 JSON 解析由 {@link LlmVerdictParser} 完成，非法时追加强约束重试 1 次，仍非法 → Invalid。纯核心无异常流.
     *
     * <p>公开可见：供单测及验收测试直接验证 LLM 契约与重试逻辑.
     */
    public LlmVerdict.LlmChatPort<LlmVerdict> buildLlmPort() {
        return buildLlmPort(new AtomicInteger());
    }

    /** 解析失败聚告警阈值：任务内语义项最终解析失败达到该数量即判定配置性问题而非内容问题（lessons #201）. */
    static final int PARSE_FAILURE_ALERT_THRESHOLD = 3;

    /**
     * 带解析失败计数的端口构造：计数器由任务级 executeAsync 持有，
     * 任务完成时达到 {@link #PARSE_FAILURE_ALERT_THRESHOLD} 即输出 ERROR 聚告警.
     */
    public LlmVerdict.LlmChatPort<LlmVerdict> buildLlmPort(final AtomicInteger parseFailureCounter) {
        // 首次解析失败才重试（附严格 JSON 约束）；Noop/Failure 不重试，仍非法 → Invalid 并计数
        return prompt -> {
            Optional<LlmVerdict.ChatOutcome<LlmVerdict>> first = chatAndParse(prompt);
            if (first.isPresent()) {
                return first.get();
            }
            log.warn("LLM 裁决响应解析失败，追加严格 JSON 约束重试 1 次");
            return chatAndParse(prompt + STRICT_JSON_RETRY_SUFFIX)
                    .orElseGet(() -> {
                        parseFailureCounter.incrementAndGet();
                        return LlmVerdict.invalid();
                    });
        };
    }

    /**
     * 单次结构化调用 + 容错解析：
     * 1. 调用 aiProvider.chatCompletionJsonObject 走 response_format=json_object 结构化通道；
     * 2. AI 未启用 → Noop；调用异常 → Failure；
     * 3. 经 LlmVerdictParser 多层容错解析，成功为终态；
     * 4. 解析失败记录原始响应（截断）后返回 empty（供 buildLlmPort 触发单次重试）.
     */
    private Optional<LlmVerdict.ChatOutcome<LlmVerdict>> chatAndParse(final String prompt) {
        String response;
        try {
            response = aiProvider.chatCompletionJsonObject(prompt, LLM_ADJUDICATION_MAX_TOKENS);
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
