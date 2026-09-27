// Input: ScoreItem 清单 + ScoreResult 映射（itemId → result）
// Output: ScoreScoringResultsDTO（逐项 DTO + 汇总 Summary）
// Pos: scoreparse/application — 打分结果装配纯函数（无仓储/无状态副作用）
// 维护声明: 维护者按项目SOP；2026-08-19 自 ScoreScoringAppService 拆出以满足 300 行预算（SRP：编排与装配分离）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreResult;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 打分结果装配：items + results → {@link ScoreScoringResultsDTO}（含汇总，纯函数）。 */
class ScoreScoringResultsAssembler {

    private final SummaryAggregator summaryAggregator = new SummaryAggregator();

    ScoreScoringResultsDTO assemble(List<ScoreItem> items, Map<Long, ScoreResult> resultMap) {
        if (items.isEmpty()) {
            return new ScoreScoringResultsDTO(List.of(), emptySummary());
        }
        List<ScoreScoringResultsDTO.ScoreResultDTO> resultDTOs = items.stream()
                .map(item -> toResultDTO(item, resultMap.get(item.getId()))).toList();
        SummaryAggregator.Result summary = summaryAggregator.aggregate(items.stream()
                .map(item -> new SummaryAggregator.Item(item.getWeight(), item.getScoreType(),
                        valueOrNull(resultMap.get(item.getId()), r -> "LEGACY_UNVERIFIED".equals(r.getReuseKind()) ? null : r.getActualScore()),
                        valueOrNull(resultMap.get(item.getId()), ScoreResult::getStatusStage2)))
                .toList());
        return new ScoreScoringResultsDTO(resultDTOs, new ScoreScoringResultsDTO.Summary(
                summary.totalWeight(), summary.totalEstScore(),
                summary.okCount(), summary.dangerCount(), summary.pendingCount(),
                summary.objectiveWeight(), summary.subjectiveWeight(), summary.weightWarning()));
    }

    private ScoreScoringResultsDTO.ScoreResultDTO toResultDTO(ScoreItem item, ScoreResult result) {
        return new ScoreScoringResultsDTO.ScoreResultDTO(
                item.getId(), item.getCode(), item.getDim(), item.getDetail(), item.getWeight(), item.getScoreType(),
                result == null ? null : result.getStatusStage2(), result == null ? null : result.getActualScore(),
                result == null ? null : result.getEvidence(), result == null ? null : result.getQuote(),
                result == null ? null : result.getMissedReason(), result == null ? null : result.getSuggestion(),
                result == null ? null : result.getMatchRatio(), result == null ? null : result.getReuseKind());
    }

    private ScoreScoringResultsDTO.Summary emptySummary() {
        return new ScoreScoringResultsDTO.Summary(
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private static <T> T valueOrNull(ScoreResult result, java.util.function.Function<ScoreResult, T> getter) {
        return result == null ? null : getter.apply(result);
    }
}
