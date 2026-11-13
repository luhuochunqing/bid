// Input: ScoreItem 清单 + ScoreResult 映射（itemId → result）
// Output: ScoreScoringResultsDTO（逐项 DTO + 汇总 Summary）
// Pos: scoreparse/application — 打分结果装配纯函数（无仓储/无状态副作用）
// 维护声明: 维护者按项目SOP；2026-08-19 自 ScoreScoringAppService 拆出以满足 300 行预算（SRP：编排与装配分离）
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.domain.SummaryAggregator;
import com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.dto.ScoreScoringResultsDTO;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreResult;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/** 打分结果装配：items + results → {@link ScoreScoringResultsDTO}（含汇总，纯函数）。 */
class ScoreScoringResultsAssembler {

    private final SummaryAggregator summaryAggregator = new SummaryAggregator();

    ScoreScoringResultsDTO assemble(List<ScoreItem> items, Map<Long, ScoreResult> resultMap) {
        return assemble(items,resultMap,null,Map.of());
    }
    ScoreScoringResultsDTO assemble(List<ScoreItem> items,Map<Long,ScoreResult> resultMap,
            ScoreParseTask latest,Map<Long,ScoreResultReusePolicy.Source> sources) {
        if (items.isEmpty()) {
            return new ScoreScoringResultsDTO(List.of(), emptySummary());
        }
        List<ScoreScoringResultsDTO.ScoreResultDTO> resultDTOs = items.stream()
                .map(item -> toResultDTO(item, resultMap.get(item.getId()),sources.get(item.getId()))).toList();
        SummaryAggregator.Result summary = summaryAggregator.aggregate(items.stream()
                .map(item -> {
                    var row=resultMap.get(item.getId());
                    String status=row==null?null:row.getStatusStage2();
                    BigDecimal est="LEGACY_UNVERIFIED".equals(row==null?null:row.getReuseKind())?null
                            :valueOrNull(row, ScoreResult::getActualScore);
                    return new SummaryAggregator.Item(item.getWeight(), item.getScoreType(),
                            ScoreStatusPolicy.OK.equals(status)?est:null, status);
                })
                .toList());
        var quality=summaryAggregator.quality(items.stream().map(item->{
            var row=resultMap.get(item.getId());var source=sources.get(item.getId());
            String outcome=row==null?"NEEDS_REVIEW":ScoringRunReportCodec.outcome(row);
            return new SummaryAggregator.QualityItem(item.getScoreType(),row==null?null:row.getActualScore(),
                    item.getWeight(),source==null?outcome:source.outcome(),source!=null,
                    row==null?null:row.getStatusStage2());
        }).toList());
        ScoringExtractionReport coverage=null;
        if(latest!=null) {
            var node=new ScoringRunReportCodec().scoring(latest.getRunSummary()).path("input").path("extraction");
            try{if(node.isObject())coverage=new com.fasterxml.jackson.databind.ObjectMapper().treeToValue(node,ScoringExtractionReport.class);}
            catch(JsonProcessingException ignored) { /* Legacy diagnostics remain unknown. */ }
        }
        return new ScoreScoringResultsDTO(resultDTOs, new ScoreScoringResultsDTO.Summary(
                summary.totalWeight(), summary.totalEstScore(),
                summary.okCount(), summary.dangerCount(), summary.pendingCount(),
                summary.objectiveWeight(), summary.subjectiveWeight(), summary.weightWarning()),
                quality.qualityStatus(),coverage,quality.objectiveScored(),quality.objectivePending(),
                quality.subjectivePending(),quality.failed(),quality.diagnosticsKnown());
    }

    private ScoreScoringResultsDTO.ScoreResultDTO toResultDTO(ScoreItem item, ScoreResult result,ScoreResultReusePolicy.Source source) {
        return new ScoreScoringResultsDTO.ScoreResultDTO(
                item.getId(), item.getCode(), item.getDim(), item.getDetail(), item.getWeight(), item.getScoreType(),
                result == null ? null : result.getStatusStage2(), result == null ? null : result.getActualScore(),
                result == null ? null : result.getEvidence(), result == null ? null : result.getQuote(),
                result == null ? null : result.getMissedReason(), result == null ? null : result.getSuggestion(),
                result == null ? null : result.getMatchRatio(), result == null ? null : result.getReuseKind(),
                result!=null&&"SOURCE_EXPIRED".equals(result.getReuseKind())?"NEEDS_REVIEW":source==null?"UNKNOWN":source.outcome(),
                result!=null&&"SOURCE_EXPIRED".equals(result.getReuseKind())?"SOURCE_DOCUMENT_CHANGED":null,
                source==null?null:source.originTaskId(),source==null?null:source.identity().fileHash(),
                source==null?null:source.identity().ruleFingerprint(),source==null?null:source.identity().engineVersion(),
                source==null?null:source.coverageStatus());
    }

    private ScoreScoringResultsDTO.Summary emptySummary() {
        return new ScoreScoringResultsDTO.Summary(
                BigDecimal.ZERO, BigDecimal.ZERO, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, false);
    }

    private static <T> T valueOrNull(ScoreResult result, java.util.function.Function<ScoreResult, T> getter) {
        return result == null ? null : getter.apply(result);
    }
}
