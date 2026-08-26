// Input: 去噪候选池 + 招标文档全文（评分章节定位在其内）
// Output: 全局归并后的最终候选清单（降级/守卫全滤时原池返回 = fail-open）
// Pos: scoreparse/application — 归并裁决编排（spec 055 FR-1/FR-3/FR-6）
// 维护声明: 维护者按项目SOP；LLM 调用委托 ScoreGlobalMergeExecutor，守卫纯核心 ScoreMergeResultGuard
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.infrastructure.openai.TenderIntakeTextProcessor;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreMergeResultGuard;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreGlobalMergeExecutor;
import com.xiyu.bid.scoreparse.infrastructure.structure.MarkdownScoreSectionLocator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 全局归并编排（spec 055 两阶段架构的裁决阶段接入点）。
 * <p>语义：候选 ≥2 才裁决；LLM 不可用 / 守卫全滤 → 保持启发式结果（fail-open，宪法 VII）；
 * 摘要日志含池/归并/采纳/幻觉/回补计数（FR-6 可观测）。
 * <p>executor 经 {@link ObjectProvider} 懒解析：e2e profile 无该 Bean 时天然降级
 * （spec 048 E2eNoopScoreAnalyzer 同款启动崩溃的预防）。
 */
@Service
@Slf4j
public class ScoreGlobalMergeOrchestrator {

    /** 裁决上下文（评分章节拼接或全文前缀）字符上界 */
    static final int SECTION_TEXT_LIMIT = 12000;

    private final ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider;

    public ScoreGlobalMergeOrchestrator(ObjectProvider<ScoreGlobalMergeExecutor> mergeExecutorProvider) {
        this.mergeExecutorProvider = mergeExecutorProvider;
    }

    /** 候选 <2 原样返回（无归并价值，不触发 LLM） */
    public List<ScoreCandidate> applyGlobalMerge(List<ScoreCandidate> valid, String fullText) {
        if (valid == null || valid.size() < 2) {
            return valid;
        }
        ScoreGlobalMergeExecutor mergeExecutor = mergeExecutorProvider.getIfAvailable();
        if (mergeExecutor == null) {
            log.info("全局归并执行器不可用（e2e 桩环境），保持启发式结果: pool={}", valid.size());
            return valid;
        }
        String sectionText = resolveSectionText(fullText);
        List<ScoreCandidate> resolved = mergeExecutor.resolve(valid, sectionText);
        if (resolved.isEmpty()) {
            log.info("全局归并降级（LLM 不可用或前置不足），保持启发式结果: pool={}", valid.size());
            return valid;
        }
        ScoreMergeResultGuard.Outcome outcome = ScoreMergeResultGuard.guard(valid, resolved, sectionText);
        if (outcome.accepted().isEmpty()) {
            log.warn("全局归并输出全部未通过守卫，保持启发式结果: pool={}, hallucinationDropped={}",
                    valid.size(), outcome.hallucinationDropped());
            return valid;
        }
        log.info("全局归并完成: pool={} → merged={} accepted={} hallucinationDropped={} restored={}",
                valid.size(), resolved.size(), outcome.accepted().size(),
                outcome.hallucinationDropped(), outcome.restoredCount());
        return outcome.accepted();
    }

    /** 裁决上下文：评分区域定位内容拼接；定位失败降级全文前缀（research R1） */
    private String resolveSectionText(String fullText) {
        if (fullText == null || fullText.isBlank()) {
            return "";
        }
        List<MarkdownScoreSectionLocator.ScoreSection> sections =
                MarkdownScoreSectionLocator.locate(fullText);
        if (sections.isEmpty()) {
            return truncate(TenderIntakeTextProcessor.sanitizeUntrusted(fullText));
        }
        StringBuilder sb = new StringBuilder();
        for (MarkdownScoreSectionLocator.ScoreSection section : sections) {
            if (sb.length() >= SECTION_TEXT_LIMIT) {
                break;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(section.content());
        }
        return truncate(TenderIntakeTextProcessor.sanitizeUntrusted(sb.toString()));
    }

    private static String truncate(String text) {
        return text.length() <= SECTION_TEXT_LIMIT ? text : text.substring(0, SECTION_TEXT_LIMIT);
    }
}
