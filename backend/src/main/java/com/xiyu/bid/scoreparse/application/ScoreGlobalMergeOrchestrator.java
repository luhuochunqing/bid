// Input: 去噪候选池 + 招标文档全文（评分章节定位在其内）
// Output: 全局归并后的最终候选清单 + MergeStats（降级/守卫全滤时原池返回 = fail-open）
// Pos: scoreparse/application — 归并裁决编排（spec 055 FR-1/FR-3/FR-6；spec 057 语料完整性 FR-01/02/03）
// 维护声明: 维护者按项目SOP；LLM 调用委托 ScoreGlobalMergeExecutor，守卫纯核心 ScoreMergeResultGuard
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreMergeResultGuard;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
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

        static MergeStats skipped(String reason) {
            return new MergeStats(null, false, reason, false, 0, 0, 0);
        }

        /** 前置检查未通过即跳过；语义区分见 Javadoc（当前实现与 skipped 相同，调用方含义不同） */
        static MergeStats fallback(String reason) {
            return new MergeStats(null, false, reason, false, 0, 0, 0);
        }
    }

    private final ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;

    public ScoreGlobalMergeOrchestrator(ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider) {
        this.mergeExecutorProvider = mergeExecutorProvider;
    }

    /** 旧签名兼容入口：未裁决（skip/fallback）时 fail-open 返回原池，裁决时返回守卫采纳清单 */
    public List<ScoreCandidate> applyGlobalMerge(List<ScoreCandidate> valid, String fullText) {
        MergeStats stats = applyGlobalMergeDetailed(valid, fullText);
        return stats.invoked() ? stats.candidates() : valid;
    }

    /** 完整归并：返回候选清单 + 全量遥测计数（spec 057 run-summary 数据源） */
    public MergeStats applyGlobalMergeDetailed(List<ScoreCandidate> valid, String fullText) {
        if (valid == null || valid.size() < 2) {
            return MergeStats.skipped("POOL_UNDER_TWO");
        }
        ScoreGlobalMergeExecutor mergeExecutor = mergeExecutorProvider.getIfAvailable();
        if (mergeExecutor == null) {
            log.info("全局归并执行器不可用（e2e 桩环境），保持启发式结果: pool={}", valid.size());
            return MergeStats.fallback("EXECUTOR_UNAVAILABLE");
        }
        AdjudicationContext ctx = resolveSectionText(fullText);
        if (!ctx.located()) {
            log.info("全局归并跳过：评分章节定位失败，禁用全文前缀裁决（spec 057 FR-01），保持启发式结果: pool={}",
                    valid.size());
            return MergeStats.skipped("SECTION_LOCATION_FAILED");
        }
        List<ScoreCandidate> resolved = mergeExecutor.resolve(valid, ctx.text());
        if (resolved.isEmpty()) {
            log.info("全局归并降级（LLM 不可用或前置不足），保持启发式结果: pool={}", valid.size());
            return MergeStats.fallback("LLM_UNAVAILABLE");
        }
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(valid, resolved, ctx.text());
        if (outcome.accepted().isEmpty()) {
            log.warn("全局归并输出全部未通过守卫，保持启发式结果: pool={}, hallucinationDropped={}",
                    valid.size(), outcome.hallucinationDropped());
            return MergeStats.fallback("GUARD_FILTERED_ALL");
        }
        log.info("全局归并完成: pool={} → merged={} accepted={} hallucinationDropped={} restored={} "
                        + "poolBackedKept={} truncated={}",
                valid.size(), resolved.size(), outcome.accepted().size(),
                outcome.hallucinationDropped(), outcome.restoredCount(),
                outcome.poolBackedKept(), ctx.truncated());
        return new MergeStats(outcome.accepted(), true, null, ctx.truncated(),
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
}
