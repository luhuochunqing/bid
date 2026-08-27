// Input: 召回遥测 RecallOutcome + 召回五表格行候选 + 进度回调
// Output: Processed(最终候选清单 / 归一化原合计 / 权重终态 / runSummaryJson / 失败消息)
// Pos: scoreparse/application — 解析后治理编排（自 ScoreParseAppService 抽出，spec 057 FR-07/D5 预案）
// 维护声明: 纯编排：合并去噪→守卫→归并裁决→完整性回补→权重治理→run-summary 装配；落库仍归 AppService
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.GhostCandidateFilter;
import com.xiyu.bid.scoreparse.domain.ItemCountCheck;
import com.xiyu.bid.scoreparse.domain.RecallOutcome;
import com.xiyu.bid.scoreparse.domain.ScoreCandidate;
import com.xiyu.bid.scoreparse.domain.ScoreItemMergePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreParseConstants;
import com.xiyu.bid.scoreparse.domain.WeightNormalizationPolicy;
import com.xiyu.bid.scoreparse.domain.WeightOverloadTrimPolicy;
import com.xiyu.bid.scoreparse.domain.WeightSumCheck;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 候选治理后处理器（spec 057 抽取，替身验证语义与抽取前完全一致）。
 * <p>链条：合并去重 → 幽灵过滤 → 无效权重过滤 → 全局归并裁决（fail-open）→
 * 完整性回补（GapRecheck）→ 超载剔除（FR-002）→ 归一化收敛（spec 047）→ 数量校验（FR-006/007）
 * → run-summary 装配（FR-04/06，含阈值告警判定，日志在此输出）。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ScoreCandidatePostProcessor {

    private static final BigDecimal OVERLOAD_LIMIT = ScoreParseConstants.OVERLOAD_LIMIT;
    private static final BigDecimal MAX_SINGLE_WEIGHT = ScoreParseConstants.MAX_SINGLE_WEIGHT;

    /** 阶段进度回调：(progress, stageKey) —— 语义与既有 updateProgress 对齐 */
    @FunctionalInterface
    public interface StageReporter {
        void report(int progress, String stageKey);
    }

    /**
     * 治理产物。failMessage 非空表示数量校验失败（0 项终态），调用方负责 stateService.failTask。
     */
    public record Processed(List<ScoreCandidate> candidates, BigDecimal originalTotal,
            WeightSumCheck.Result weightResult, String runSummaryJson, String failMessage) {
    }

    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final ScoreGlobalMergeOrchestrator globalMergeOrchestrator;

    private final ScoreItemMergePolicy mergePolicy = new ScoreItemMergePolicy();
    private final GhostCandidateFilter ghostFilter = new GhostCandidateFilter();
    private final WeightSumCheck weightSumCheck = new WeightSumCheck();
    private final ItemCountCheck itemCountCheck = new ItemCountCheck();
    private final WeightOverloadTrimPolicy trimPolicy = new WeightOverloadTrimPolicy();
    private final WeightNormalizationPolicy normalizationPolicy = new WeightNormalizationPolicy();

    public Processed process(String taskId, String fullText, RecallOutcome recall,
            List<ScoreCandidate> tableRows, StageReporter reporter) {
        List<ScoreCandidate> candidates = new ArrayList<>(recall.candidates());
        candidates.addAll(tableRows);
        List<ScoreCandidate> merged = mergePolicy.merge(candidates);
        List<ScoreCandidate> ghostFiltered = ghostFilter.filter(merged);
        log.info("幽灵候选过滤（spec 046 FR-001）: taskId={} {}→{}", taskId, merged.size(), ghostFiltered.size());
        List<ScoreCandidate> valid = filterInvalidWeights(ghostFiltered);

        var stats = globalMergeOrchestrator.applyGlobalMergeDetailed(valid, fullText);
        if (stats.invoked()) {
            valid = stats.candidates();
        }

        WeightSumCheck.Result weightResult = weightSumCheck.checkCandidates(valid);
        ItemCountCheck.Result continuityResult = itemCountCheck.checkCandidates(valid);
        if (weightResult.needRecheck() || continuityResult.needRecheck()) {
            reporter.report(80, "GAP_RECHECK");
            List<ScoreCandidate> missed = scoreAnalyzer.recheckGaps(fullText, valid);
            if (!missed.isEmpty()) {
                log.info("完整性回补发现遗漏项: taskId={}, missed={}", taskId, missed.size());
                valid = filterInvalidWeights(mergePolicy.merge(
                        java.util.stream.Stream.concat(valid.stream(), missed.stream()).toList()));
                weightResult = weightSumCheck.checkCandidates(valid);
            }
        }
        if (weightResult.totalWeight().compareTo(OVERLOAD_LIMIT) > 0) {
            var trimmed = trimPolicy.trim(valid, OVERLOAD_LIMIT);
            valid = trimmed.remaining();
            log.warn("权重超载剔除 spec046: removed={}", trimmed.removed().size());
            weightResult = weightSumCheck.checkCandidates(valid);
        }
        var normalized = normalizationPolicy.normalize(valid);
        BigDecimal originalTotal = null;
        if (normalized.originalTotal() != null) {
            valid = normalized.normalizedCandidates();
            originalTotal = normalized.originalTotal().setScale(2, java.math.RoundingMode.HALF_UP);
            weightResult = weightSumCheck.checkCandidates(valid);
        }
        ItemCountCheck.Result countResult = itemCountCheck.check(valid.size());
        if (countResult.failed()) {
            log.warn("解析结果为 0 项，判定失败: taskId={}, candidates={}", taskId, candidates.size());
            return new Processed(List.of(), null, weightResult,
                    RunSummaryAssembler.assemble(recall, tableRows.size(),
                            toView(stats), weightResult, 0, recall.llmModel()).json(),
                    countResult.failureMessage());
        }
        var assembled = RunSummaryAssembler.assemble(recall, tableRows.size(),
                toView(stats), weightResult, valid.size(), recall.llmModel());
        if (assembled.warnLowAlignment()) {
            log.warn("对齐率低于 {}: hit={}, total={} —— 模型概括改写严重，建议换模型对照重解析"
                            + "（spec 057 FR-06）",
                    RunSummaryAssembler.ALIGNED_RATIO_FLOOR, recall.alignedHit(), recall.alignedTotal());
        }
        if (assembled.warnHighHallucination()) {
            log.warn("归并幻觉丢弃率超阈（057 FR-06）: dropped={}, poolBackedKept={}, restored={}",
                    stats.hallucinationDropped(), stats.poolBackedKept(), stats.restored());
        }
        return new Processed(valid, originalTotal, weightResult, assembled.json(), null);
    }

    private static RunSummaryAssembler.MergeView toView(
            ScoreGlobalMergeOrchestrator.MergeStats stats) {
        return new RunSummaryAssembler.MergeView(stats.invoked(), stats.truncated(),
                stats.poolBackedKept(), stats.hallucinationDropped(), stats.restored());
    }

    private List<ScoreCandidate> filterInvalidWeights(List<ScoreCandidate> candidates) {
        List<ScoreCandidate> valid = new ArrayList<>();
        int dropped = 0;
        for (ScoreCandidate candidate : candidates) {
            BigDecimal w = candidate.weight();
            if (w != null && w.compareTo(BigDecimal.ZERO) > 0 && w.compareTo(MAX_SINGLE_WEIGHT) <= 0) {
                valid.add(candidate);
            } else {
                dropped++;
            }
        }
        if (dropped > 0) {
            log.warn("丢弃权重无效或超界（<=0 或 >100）的候选 {} 项（spec Edge Cases）", dropped);
        }
        return valid;
    }
}
