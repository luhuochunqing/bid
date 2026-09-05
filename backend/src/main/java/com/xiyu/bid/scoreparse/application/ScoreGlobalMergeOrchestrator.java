// Input: 去噪候选池 + 招标文档全文（评分章节定位在其内）
// Output: 全局归并后的最终候选清单 + MergeStats（降级/守卫全滤时原池返回 = fail-open）
// Pos: scoreparse/application — 归并裁决编排（spec 055 FR-1/FR-3/FR-6；spec 057 语料完整性 FR-01/02/03）
// 维护声明: 维护者按项目SOP；LLM 调用委托 ScoreGlobalMergeExecutor，守卫纯核心 ScoreMergeResultGuard
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.ScoreMergeResultGuard;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.llm.LlmDecision;
import com.xiyu.bid.scoreparse.domain.llm.LlmFactOperationValidator;
import com.xiyu.bid.scoreparse.domain.llm.RawFactOperation;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全局归并编排（spec 055 两阶段架构的裁决阶段接入点）。
 * <p>语义：候选 ≥2 才裁决；LLM 不可用 / 守卫全滤 → 保持启发式结果（fail-open，宪法 VII）；
 * <b>spec 057 FR-01：评分章节定位失败时禁用全文前缀裁决</b>——拿错语料裁决会误杀真项并污染
 * hallucinationDropped 计数；摘要日志含 pool/merged/accepted/hallucinationDropped/restored/
 * poolBackedKept/truncated（FR-6 可观测）。
 * <p>executor 经 {@link ObjectProvider} 懒解析：e2e profile 无该 Bean 时天然降级。
 */
@Service
@Slf4j
public class ScoreGlobalMergeOrchestrator {

    /** 归并结果与遥测投影（run-summary 数据源；candidates 兼容旧语义） */
    public record MergeStats(List<ScoreCandidate> candidates, boolean invoked, String skipReason,
            boolean truncated, int poolBackedKept, int hallucinationDropped, int restored) {

        /**
         * 未裁决的统一工厂（设计弯路审查 P6）：skip（前置未过）与 fallback（执行后降级）的分类学
         * 完全由 reason 承载——POOL_UNDER_TWO/SECTION_LOCATION_FAILED 为前置跳过，
         * EXECUTOR_UNAVAILABLE/LLM_UNAVAILABLE/GUARD_FILTERED_ALL 为执行后降级。
         * 调用方语义一律 fail-open 保持启发式候选池。
         */
        static MergeStats notInvoked(String reason) {
            return new MergeStats(null, false, reason, false, 0, 0, 0);
        }
    }

    /** 事实裁决结果与决策状态（spec 059 Phase 3） */
    public record FactMergeOutcome(
            List<ScoreFact> facts,
            boolean invoked,
            String skipReason,
            List<String> violations,
            ScoreParseDecision decisionStatus,
            String fallbackReason
    ) {
        public static FactMergeOutcome notInvoked(List<ScoreFact> facts, String skipReason, String fallbackReason) {
            ScoreParseDecision dec = ScoreFactValidator.validateScoringSet(facts).scoringComplete()
                    ? ScoreParseDecision.AUTO_APPROVED : ScoreParseDecision.REVIEW_REQUIRED;
            return new FactMergeOutcome(facts, false, skipReason, List.of(), dec, fallbackReason);
        }
    }

    private final ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;

    public ScoreGlobalMergeOrchestrator(ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider) {
        this.mergeExecutorProvider = mergeExecutorProvider;
    }

    /** 旧签名兼容入口：未裁决（skip/fallback）时 fail-open 返回原池，裁决时返回守卫采纳清单 */
    public List<ScoreCandidate> applyGlobalMerge(List<ScoreCandidate> valid, String fullText) {
        return applyGlobalMerge(valid, fullText, null);
    }

    public List<ScoreCandidate> applyGlobalMerge(List<ScoreCandidate> valid, String fullText, String vocabularyExamples) {
        MergeStats stats = applyGlobalMergeDetailed(valid, fullText, vocabularyExamples);
        return stats.invoked() ? stats.candidates() : valid;
    }

    /** 完整归并：返回候选清单 + 全量遥测计数（spec 057 run-summary 数据源） */
    public MergeStats applyGlobalMergeDetailed(List<ScoreCandidate> valid, String fullText) {
        return applyGlobalMergeDetailed(valid, fullText, null);
    }

    /**
     * 完整归并（含词汇注册表 escape-hatch）。
     * @param vocabularyExamples {@link com.xiyu.bid.scoreparse.domain.ScoreVocabularyRegistry#toPromptExamples} 输出，null 表示不注入
     */
    public MergeStats applyGlobalMergeDetailed(List<ScoreCandidate> valid, String fullText, String vocabularyExamples) {
        if (valid == null || valid.size() < 2) {
            return MergeStats.notInvoked("POOL_UNDER_TWO");
        }
        ScoreGlobalMergeExecutor mergeExecutor = mergeExecutorProvider.getIfAvailable();
        if (mergeExecutor == null) {
            log.info("全局归并执行器不可用（e2e 桩环境），保持启发式结果: pool={}", valid.size());
            return MergeStats.notInvoked("EXECUTOR_UNAVAILABLE");
        }
        AdjudicationContext ctx = resolveSectionText(fullText);
        if (!ctx.located()) {
            log.info("全局归并跳过：评分章节定位失败，禁用全文前缀裁决（spec 057 FR-01），保持启发式结果: pool={}",
                    valid.size());
            return MergeStats.notInvoked("SECTION_LOCATION_FAILED");
        }
        List<ScoreCandidate> resolved = mergeExecutor.resolve(valid, ctx.text(), vocabularyExamples);
        if (resolved.isEmpty()) {
            log.info("全局归并降级（LLM 不可用或前置不足），保持启发式结果: pool={}", valid.size());
            return MergeStats.notInvoked("LLM_UNAVAILABLE");
        }
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(valid, resolved, ctx.text());
        if (outcome.accepted().isEmpty()) {
            log.warn("全局归并输出全部未通过守卫，保持启发式结果: pool={}, hallucinationDropped={}",
                    valid.size(), outcome.hallucinationDropped());
            return MergeStats.notInvoked("GUARD_FILTERED_ALL");
        }
        // Phase 3a: 结构化通道候选（源自 Word XML）不可被 LLM 归并替换；若被丢弃则回注
        List<ScoreCandidate> accepted = outcome.accepted();
        List<ScoreCandidate> tableCandidates = valid.stream()
                .filter(c -> "STRUCTURED_TABLE".equals(c.semanticPattern()) || "TABLE_GRID".equals(c.semanticPattern()))
                .toList();
        if (!tableCandidates.isEmpty()) {
            List<String> acceptedNorm = accepted.stream()
                    .map(c -> normalizeText(c.detail()))
                    .filter(s -> !s.isEmpty())
                    .toList();
            List<ScoreCandidate> restored = tableCandidates.stream()
                    .filter(c -> {
                        String nd = normalizeText(c.detail());
                        return !nd.isEmpty() && acceptedNorm.stream().noneMatch(a -> a.contains(nd) || nd.contains(a));
                    })
                    .toList();
            if (!restored.isEmpty()) {
                log.info("全局归并回注表格候选（LLM 不可替代表格权威数据）: restored={}", restored.size());
                accepted = new java.util.ArrayList<>(accepted);
                accepted.addAll(restored);
            }
        }
        log.info("全局归并完成: pool={} → merged={} accepted={} hallucinationDropped={} restored={} "
                        + "poolBackedKept={} truncated={}",
                valid.size(), resolved.size(), accepted.size(),
                outcome.hallucinationDropped(), outcome.restoredCount(),
                outcome.poolBackedKept(), ctx.truncated());
        return new MergeStats(accepted, true, null, ctx.truncated(),
                outcome.poolBackedKept(), outcome.hallucinationDropped(), outcome.restoredCount());
    }

    /** 裁决上下文：定位失败 → located=false（057 FR-01 禁止全文前缀降级）；located 时带 truncated 标记 */
    private record AdjudicationContext(String text, boolean truncated) {
        boolean located() {
            return text != null && !text.isBlank();
        }
    }

    /**
     * 裁决上下文构建：评分区域定位内容拼接，超 {@link ScoreParseConstants#SECTION_TEXT_LIMIT}
     * 截断并置 truncated（FR-03）。截断判定单点：拼接中途达限（stopEarly）或 sanitize 后仍超限
     * （sanitize 是等长字符替换，此分支为防御性兜底）。
     */
    private AdjudicationContext resolveSectionText(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return new AdjudicationContext("", false);
        }
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(fullText);
        if (sections.isEmpty()) {
            return new AdjudicationContext("", false);
        }
        StringBuilder sb = new StringBuilder();
        boolean stopEarly = false;
        for (MarkdownScoreSectionLocator.ScoreSection section : sections) {
            if (sb.length() >= ScoreParseConstants.SECTION_TEXT_LIMIT) {
                stopEarly = true;
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(section.content());
        }
        String sanitized = TenderIntakeTextProcessor.sanitizeUntrusted(sb.toString());
        // sanitize 为等长替换，正常不触发；保留防御性判定使截断标记单点收口于下一行
        boolean truncated = stopEarly || sanitized.length() > ScoreParseConstants.SECTION_TEXT_LIMIT;
        return new AdjudicationContext(
                truncate(sanitized), truncated);
    }

    private static String truncate(String text) {
        return text.length() <= ScoreParseConstants.SECTION_TEXT_LIMIT
                ? text : text.substring(0, ScoreParseConstants.SECTION_TEXT_LIMIT);
    }

    private static String normalizeText(String text) {
        return text == null ? "" : text.replaceAll("\\s+", "");
    }

    /**
     * 针对事实池的语义裁决编排（spec 059 Phase 3：T303）。
     * <p>仅在确定性事实池上运行；禁止 LLM 生成评分项清单；
     * 当 LLM 不可用或跳过时，fail-open 保持确定性事实，保持 AUTO_APPROVED 或 REVIEW_REQUIRED 且可复现；
     * 当 LLM 违规时，不回写事实，受影响项转 REVIEW_REQUIRED。
     */
    public FactMergeOutcome adjudicateFacts(List<ScoreFact> facts, String fullText) {
        if (facts == null || facts.isEmpty()) {
            return FactMergeOutcome.notInvoked(List.of(), "EMPTY_FACTS", "ZERO_VALID_ITEMS");
        }
        ScoreGlobalMergeExecutor mergeExecutor = mergeExecutorProvider.getIfAvailable();
        if (mergeExecutor == null) {
            log.info("全局归并执行器不可用（桩环境），保持确定性事实: count={}", facts.size());
            return FactMergeOutcome.notInvoked(facts, "EXECUTOR_UNAVAILABLE", null);
        }
        AdjudicationContext ctx = resolveSectionText(fullText);
        if (!ctx.located()) {
            log.info("评分章节定位失败，保持确定性事实: count={}", facts.size());
            return FactMergeOutcome.notInvoked(facts, "SECTION_LOCATION_FAILED", "RESORT_TO_MARKDOWN");
        }
        List<RawFactOperation> rawOps = mergeExecutor.resolveRawFacts(facts, ctx.text());
        if (rawOps.isEmpty()) {
            log.info("LLM 未返回事实操作或调用失败，保持确定性事实: count={}", facts.size());
            return FactMergeOutcome.notInvoked(facts, "LLM_UNAVAILABLE", null);
        }
        LlmFactOperationValidator.ValidationResult validation =
                LlmFactOperationValidator.validateRaw(facts, rawOps);
        if (validation.hasViolations()) {
            log.warn("LLM 事实操作校验未通过，转人工复核不回写: count={}, violations={}",
                    facts.size(), validation.violations());
            List<ScoreFact> reviewFacts = LlmFactOperationValidator.markViolatedFacts(facts, rawOps);
            return new FactMergeOutcome(reviewFacts, true, null, validation.violations(),
                    ScoreParseDecision.REVIEW_REQUIRED, "RESORT_TO_LLM_MERGE");
        }
        LlmDecision decision = RawFactOperation.toDecision(rawOps);
        List<ScoreFact> applied = LlmFactOperationValidator.apply(facts, decision);
        var scoringValidation = ScoreFactValidator.validateScoringSet(applied);
        ScoreParseDecision decisionStatus = scoringValidation.scoringComplete()
                ? ScoreParseDecision.AUTO_APPROVED : ScoreParseDecision.REVIEW_REQUIRED;
        String fallback = scoringValidation.scoringComplete() ? null : "WEIGHT_GAP_OR_STRUCTURE_DEFECT";
        return new FactMergeOutcome(applied, true, null, List.of(), decisionStatus, fallback);
    }
}
