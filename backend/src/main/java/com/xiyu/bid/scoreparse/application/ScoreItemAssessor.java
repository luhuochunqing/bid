package com.xiyu.bid.scoreparse.application;

import com.openai.errors.BadRequestException;
import com.xiyu.bid.scoreparse.domain.DocumentImage;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.KnowledgeCategoryPolicy;
import com.xiyu.bid.scoreparse.domain.PartialScorePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreAssessmentGuard;
import com.xiyu.bid.scoreparse.domain.ScoreStatusPolicy;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * 单项 LLM 对标与沿用拷贝。
 * <p>spec 048 US1（T018）：客观项携带投标文件图片走多模态打分；
 * 配给 = 预算内全量携带（index 顺序确定性截断，US2 换 ImageAssigner 章节配给）；
 * 降级矩阵见 contracts/transport-multimodal.md——无图/损坏项 → 纯文本，
 * 多模态不被支持（BadRequestException / RESPONSES 风格 ISE）→ 纯文本重试一次 + Sentry，不阻断任务。
 */
@Slf4j
final class ScoreItemAssessor {

    private static final int BID_DOC_EXCERPT_MAX_CHARS = 12000;

    private final OpenAiScoreAnalyzer scoreAnalyzer;
    private final ImageBudget imageBudget;
    private final ScoreAssessmentGuard assessmentGuard = new ScoreAssessmentGuard();
    private final ScoreStatusPolicy statusPolicy = new ScoreStatusPolicy();
    private final PartialScorePolicy partialScorePolicy = new PartialScorePolicy();
    private final KnowledgeCategoryPolicy categoryPolicy = new KnowledgeCategoryPolicy();

    ScoreItemAssessor(OpenAiScoreAnalyzer scoreAnalyzer, ImageBudget imageBudget) {
        this.scoreAnalyzer = scoreAnalyzer;
        this.imageBudget = imageBudget;
    }

    ScoreResult assess(ScoreParseTask task, ScoreItem item, String bidDocText, List<DocumentImage> images) {
        if (item.getWeight() == null) return fallbackPending(task, List.of(item), "评分权重未知，无法计算").get(0);
        String excerpt = ScoreDocExcerptExtractor.extractRelevantExcerpt(
                bidDocText, item, BID_DOC_EXCERPT_MAX_CHARS);
        boolean subjective = ScoreAssessmentGuard.TYPE_SUBJECTIVE.equals(item.getScoreType());
        ScoreAssessmentOutput output = subjective
                ? scoreAnalyzer.assessSubjective(item.getDetail(), excerpt)
                : assessObjective(item, excerpt, allocateImagesWithinBudget(images));
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

    ScoreResult assessScoring(ScoreParseTask task,ScoreItem item,
            com.xiyu.bid.scoreparse.domain.ScoringDocument document,ScoringFileSession file,long deadline,
            com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig configuration) {
        if(item.getWeight()==null)return fallbackPending(task,List.of(item),"评分权重未知，无法计算").getFirst();
        var budget=new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget(deadline,configuration);
        String excerpt=ScoreDocExcerptExtractor.extractRelevantExcerpt(document.text(),item,BID_DOC_EXCERPT_MAX_CHARS);
        String creditRow=com.xiyu.bid.scoreparse.domain.CreditLimitScoreProof.evidenceRow(item.getDetail(),document);
        if(creditRow!=null&&!excerpt.contains(creditRow)&&creditRow.length()<=BID_DOC_EXCERPT_MAX_CHARS) {
            int remaining=BID_DOC_EXCERPT_MAX_CHARS-creditRow.length()-7;
            excerpt=creditRow+(remaining>=0?"\n\n---\n\n"+excerpt.substring(0,Math.min(excerpt.length(),remaining)):"");
        }
        boolean subjective=ScoreAssessmentGuard.TYPE_SUBJECTIVE.equals(item.getScoreType());
        ScoreAssessmentOutput output=null;
        RuntimeException failure=null;
        while(budget.available() && output==null) {
            int before=budget.attempts();
            try {
                output=subjective?scoreAnalyzer.assessSubjective(item.getDetail(),excerpt,budget)
                        :scoreAnalyzer.assessObjective(item.getDetail(),item.getWeight(),excerpt,List.of(),budget);
            }catch(RuntimeException ex){failure=ex;if(budget.attempts()==before)throw ex;}
        }
        if(output==null)throw failure==null?new IllegalStateException("SCORING_EMPTY_OUTPUT"):failure;
        if(!subjective && output.actualScore==null && budget.available()) {
            try {
                var associated=file.imagesFor(excerpt,imageBudget);
                if(!associated.isEmpty())output=scoreAnalyzer.assessObjective(item.getDetail(),item.getWeight(),excerpt,associated,budget);
            } catch(BadRequestException | IllegalStateException ex) {
                ScoringDeadline.check(deadline);
                // Text already succeeded; compatibility/schema fallback must not start a third transmission.
                log.warn("按需补图不可用，保留文本评估: itemId={}, type={}, msg={}",
                        item.getId(),ex.getClass().getSimpleName(),ex.getMessage());
                Sentry.captureException(ex);
            }
        }
        var guarded=assessmentGuard.guard(toGuardInput(output),item.getWeight(),item.getScoreType());
        BigDecimal score=resolveObjectiveScore(item,guarded,output);
        String normalizedQuote=guarded.quote()==null?"":guarded.quote().replaceAll("\\s+","");
        boolean quoteVerified=normalizedQuote.length()>=8 && document.text().contains(guarded.quote())
                &&excerpt.replaceAll("\\s+","").contains(normalizedQuote);
        boolean complete="COMPLETE".equals(document.extraction().coverageStatus());
        var credit=com.xiyu.bid.scoreparse.domain.CreditLimitScoreProof.assess(item.getDetail(),item.getWeight(),
                guarded.actualScore(),score,quoteVerified?guarded.quote():null,document);
        if(credit.highestBandVerified())score=credit.score();
        boolean localProof=credit.score()!=null || quoteVerified && document.verifiedQuoteSource(guarded.quote())
                && assessmentGuard.closedPositiveCertificate(item.getDim(),item.getDetail(),item.getWeight(),score,guarded.quote());
        if(!subjective && !assessmentGuard.canPublish(document.extraction().coverageStatus(),
                new ScoreAssessmentGuard.CoverageProof(quoteVerified,item.getDetail()!=null&&!item.getDetail().isBlank(),
                        complete&&document.extraction().emptyUnitCount()==0 || localProof)))score=null;
        return ScoreResult.builder().scoreItemId(item.getId()).scoringTaskId(task.getId())
                .actualScore(score).statusStage2(statusPolicy.evaluate(score,item.getWeight(),item.getScoreType(),false))
                .evidence(guarded.evidence()).quote(quoteVerified?guarded.quote():null)
                .missedReason(score==null&&!subjective?"可核验证据不足，请人工确认":guarded.missedReason())
                .suggestion(guarded.suggestion()).matchRatio(score==null?null:guarded.matchRatio())
                .reuseKind(subjective?"SUBJECTIVE":"FRESH").build();
    }

    /**
     * 客观项打分：配给后有图 → 多模态过载；多模态不被支持
     * （图片 part 被拒 BadRequestException / RESPONSES 风格 ISE）→ 纯文本重试恰好一次，
     * log.warn + Sentry 上报，不阻断任务（FR-005 降级矩阵）。
     */
    private ScoreAssessmentOutput assessObjective(ScoreItem item, String excerpt, List<DocumentImage> images) {
        if (images.isEmpty()) {
            return scoreAnalyzer.assessObjective(item.getDetail(), item.getWeight(), excerpt);
        }
        try {
            return scoreAnalyzer.assessObjective(item.getDetail(), item.getWeight(), excerpt, images);
        } catch (BadRequestException | IllegalStateException ex) {
            log.warn("多模态打分降级纯文本重试: itemId={}, type={}, msg={}",
                    item.getId(), ex.getClass().getSimpleName(), ex.getMessage());
            Sentry.captureException(ex);
            return scoreAnalyzer.assessObjective(item.getDetail(), item.getWeight(), excerpt);
        }
    }

    /**
     * US1 最简配给：预算内全量携带（index 顺序，超图数/字节预算即确定性截断）。
     * 损坏项（空 base64 / 空 mimeType）防御式跳过；全损坏 → 空配给走纯文本。
     */
    private List<DocumentImage> allocateImagesWithinBudget(List<DocumentImage> images) {
        if (images == null || images.isEmpty()) {
            return List.of();
        }
        List<DocumentImage> budgeted = new ArrayList<>();
        long bytes = 0;
        for (DocumentImage image : images) {
            if (image == null || image.base64() == null || image.base64().isBlank()
                    || image.mimeType() == null || image.mimeType().isBlank()) {
                log.warn("跳过损坏图片项（base64/mimeType 非法）: index={}",
                        image == null ? null : image.index());
                continue;
            }
            if (budgeted.size() >= imageBudget.maxImagesPerItem()
                    || bytes + image.base64().length() > imageBudget.maxImageBytesPerItem()) {
                log.warn("图片配给超预算截断: carried={}, budgetImages={}, bytes={}, budgetBytes={}",
                        budgeted.size(), imageBudget.maxImagesPerItem(), bytes, imageBudget.maxImageBytesPerItem());
                break;
            }
            budgeted.add(image);
            bytes += image.base64().length();
        }
        return List.copyOf(budgeted);
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
