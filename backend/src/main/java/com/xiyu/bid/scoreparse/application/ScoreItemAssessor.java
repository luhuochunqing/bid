package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreAssessmentGuard;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.List;

/** 单项 LLM 对标与沿用拷贝。 */
@Slf4j
final class ScoreItemAssessor {

    private static final int BID_DOC_EXCERPT_MAX_CHARS = 12000;

    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final ScoreAssessmentGuard assessmentGuard = new ScoreAssessmentGuard();
    private final ScoreStatusPolicy statusPolicy = new ScoreStatusPolicy();
    private final PartialScorePolicy partialScorePolicy = new PartialScorePolicy();
    private final KnowledgeCategoryPolicy categoryPolicy = new KnowledgeCategoryPolicy();

    ScoreItemAssessor(OpenAiScoreAnalyzer scoreAnalyzer) {
        this.scoreAnalyzer = scoreAnalyzer;
    }

    ScoreResult assess(ScoreParseTask task, ScoreItem item, String bidDocText) {
        String excerpt = ScoreDocExcerptExtractor.extractRelevantExcerpt(
                bidDocText, item, BID_DOC_EXCERPT_MAX_CHARS);
        ScoreAssessmentOutput output = ScoreAssessmentGuard.TYPE_SUBJECTIVE.equals(item.getScoreType())
                ? scoreAnalyzer.assessSubjective(item.getDetail(), excerpt)
                : scoreAnalyzer.assessObjective(item.getDetail(), item.getWeight(), excerpt);
        ScoreAssessmentGuard.Result assessment = assessmentGuard.guard(
                toGuardInput(output), item.getWeight(), item.getScoreType());
        if (assessment.rangeInvalid()) {
            log.warn("AI 得分超出 [0, {}] 区间，置空待确认: itemId={}", item.getWeight(), item.getId());
        }
        if (assessment.subjectiveDropped()) {
            log.info("主观项数字输出已丢弃（SC-003）: itemId={}", item.getId());
        }
        BigDecimal finalActualScore = resolveObjectiveScore(item, assessment, output);
        String status = statusPolicy.evaluate(finalActualScore, item.getWeight(), item.getScoreType(), false);
        return ScoreResult.builder()
                .scoreItemId(item.getId()).scoringTaskId(task.getId())
                .actualScore(finalActualScore).statusStage2(status)
                .evidence(assessment.evidence()).quote(assessment.quote())
                .missedReason(assessment.missedReason()).suggestion(assessment.suggestion())
                .matchRatio(assessment.matchRatio()).reuseKind("FRESH").build();
    }

    ScoreResult reuse(ScoreParseTask task, ScoreResult old) {
        return ScoreResult.builder()
                .scoreItemId(old.getScoreItemId()).scoringTaskId(task.getId())
                .actualScore(old.getActualScore()).statusStage2(old.getStatusStage2())
                .evidence(old.getEvidence()).quote(old.getQuote())
                .missedReason(old.getMissedReason()).suggestion(old.getSuggestion())
                .matchRatio(old.getMatchRatio()).reuseKind("REUSED").build();
    }

    List<ScoreResult> fallbackPending(ScoreParseTask task, List<ScoreItem> items, String reason) {
        return items.stream().map(item -> ScoreResult.builder()
                .scoreItemId(item.getId()).scoringTaskId(task.getId()).actualScore(null)
                .statusStage2("PENDING").missedReason(reason).build()).toList();
    }

    private BigDecimal resolveObjectiveScore(ScoreItem item, ScoreAssessmentGuard.Result assessment,
                                             ScoreAssessmentOutput output) {
        if (!ScoreAssessmentGuard.TYPE_OBJECTIVE.equals(item.getScoreType()) || assessment.rangeInvalid()) {
            return null;
        }
        Integer matchRatio = assessment.matchRatio() != null ? assessment.matchRatio()
                : (output != null ? output.matchRatio : null);
        if (matchRatio != null) {
            String category = categoryPolicy.categorize(item.getDim(), item.getDetail());
            return partialScorePolicy.computeStage2Score(item.getWeight(), category, matchRatio, item.getScoreType());
        }
        return assessment.actualScore();
    }

    private static ScoreAssessmentGuard.Input toGuardInput(ScoreAssessmentOutput output) {
        return ScoreAssessmentGuard.Input.builder()
                .actualScore(output.actualScore == null ? null : BigDecimal.valueOf(output.actualScore))
                .matchRatio(output.matchRatio).evidence(output.evidence).quote(output.quote)
                .quoteMissing(output.quoteMissing).missedReason(output.missedReason)
                .suggestion(output.suggestion).build();
    }
}
