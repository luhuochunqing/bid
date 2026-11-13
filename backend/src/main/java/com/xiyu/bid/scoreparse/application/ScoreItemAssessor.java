package com.xiyu.bid.scoreparse.application;

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

/** 单项评分：文本先评估，证据不足时在同一请求预算内补充关联图片。 */
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

    ScoreResult assessScoring(ScoreParseTask task,ScoreItem item,
            com.xiyu.bid.scoreparse.domain.ScoringDocument document,ScoringFileSession file,long deadline,
            com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig configuration) {
        if(item.getWeight()==null)return fallbackPending(task,List.of(item),"评分权重未知，无法计算").getFirst();
        var budget=new com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget(deadline,configuration);
        var sourceExcerpt=scoringExcerpt(document,item);
        String excerpt=sourceExcerpt.text();
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
        ScoreResult validated=validateScoring(task,item,document,excerpt,output,subjective);
        if(!subjective && validated.getActualScore()==null && budget.available()) {
            try {
                var associated=file.imagesFor(sourceExcerpt,imageBudget);
                if(!associated.isEmpty()) {
                    var supplemented=scoreAnalyzer.assessObjective(item.getDetail(),item.getWeight(),excerpt,associated,budget);
                    validated=validateScoring(task,item,document,excerpt,supplemented,false);
                }
            } catch(RuntimeException ex) {
                ScoringDeadline.check(deadline);
                // Text already succeeded; a failed supplement must not start a third transmission.
                log.warn("按需补图不可用，保留文本评估: itemId={}, type={}, msg={}",
                        item.getId(),ex.getClass().getSimpleName(),ex.getMessage());
                Sentry.captureException(ex);
            }
        }
        return validated;
    }

    private ScoreDocExcerptExtractor.Excerpt scoringExcerpt(
            com.xiyu.bid.scoreparse.domain.ScoringDocument document,ScoreItem item) {
        String creditRow=com.xiyu.bid.scoreparse.domain.CreditLimitScoreProof.evidenceRow(item.getDetail(),document);
        var excerpt=ScoreDocExcerptExtractor.extractRelevantExcerptWithSegments(
                document.text(),item.getDim(),item.getDetail(),BID_DOC_EXCERPT_MAX_CHARS);
        if(creditRow==null || excerpt.text().contains(creditRow) || creditRow.length()>BID_DOC_EXCERPT_MAX_CHARS)
            return excerpt;
        int offset=document.text().indexOf(creditRow);
        if(offset<0)return excerpt;
        int remaining=BID_DOC_EXCERPT_MAX_CHARS-creditRow.length()-7;
        var body=remaining>0?ScoreDocExcerptExtractor.extractRelevantExcerptWithSegments(
                document.text(),item.getDim(),item.getDetail(),remaining)
                :new ScoreDocExcerptExtractor.Excerpt("",List.of());
        var segments=new ArrayList<ScoreDocExcerptExtractor.Segment>();
        segments.add(new ScoreDocExcerptExtractor.Segment("credit-row",creditRow,offset));
        segments.addAll(body.segments());
        return new ScoreDocExcerptExtractor.Excerpt(
                creditRow+(body.text().isEmpty()?"":"\n\n---\n\n"+body.text()),List.copyOf(segments));
    }

    private ScoreResult validateScoring(ScoreParseTask task,ScoreItem item,
            com.xiyu.bid.scoreparse.domain.ScoringDocument document,String excerpt,
            ScoreAssessmentOutput output,boolean subjective) {
        var input=toGuardInput(output);
        var guarded=assessmentGuard.guard(input,item.getWeight(),item.getScoreType());
        BigDecimal score=resolveObjectiveScore(item,guarded,output);
        var quoteProof=assessmentGuard.verifyQuote(subjective?null:output.quote,document.text(),excerpt);
        String quote=quoteProof.quote();boolean quoteVerified=quoteProof.verified();
        boolean complete="COMPLETE".equals(document.extraction().coverageStatus());
        var credit=com.xiyu.bid.scoreparse.domain.CreditLimitScoreProof.assess(item.getDetail(),item.getWeight(),
                guarded.actualScore(),score,quote,document);
        if(credit.highestBandVerified())score=credit.score();
        boolean localProof=credit.score()!=null || quoteVerified && document.verifiedQuoteSource(quote)
                && assessmentGuard.closedPositiveCertificate(item.getDim(),item.getDetail(),item.getWeight(),score,quote);
        boolean absentZero=score!=null&&score.signum()==0&&assessmentGuard.verifiedAbsentCertificate(
                input,item.getDim(),item.getDetail(),item.getWeight(),document);
        if(!subjective && !absentZero && !assessmentGuard.canPublish(document.extraction().coverageStatus(),
                new ScoreAssessmentGuard.CoverageProof(quoteVerified,item.getDetail()!=null&&!item.getDetail().isBlank(),
                        complete&&document.extraction().emptyUnitCount()==0 || localProof)))score=null;
        String rejection=guarded.rangeInvalid()?"SCORE_OUT_OF_RANGE":!quoteVerified?quoteProof.reason():"COVERAGE_PROOF_INSUFFICIENT";
        if(!subjective&&(score==null||Boolean.TRUE.equals(output.quoteMissing)&&quoteVerified))
            log.info("评分引用诊断: itemId={}, rawScore={}, quoteMissing={}, quoteLength={}, documentRawMatch={}, reason={}",
                    item.getId(),output.actualScore,output.quoteMissing,output.quote==null?0:output.quote.length(),
                    output.quote!=null&&document.text().contains(output.quote),score==null?rejection:"VERIFIED_FLAG_CONFLICT");
        return ScoreResult.builder().scoreItemId(item.getId()).scoringTaskId(task.getId())
                .actualScore(score).statusStage2(statusPolicy.evaluate(score,item.getWeight(),item.getScoreType(),false))
                .evidence(guarded.evidence()).quote(quote)
                .missedReason(score==null&&!subjective?"可核验证据不足，请人工确认（"+rejection+"）":guarded.missedReason())
                .suggestion(guarded.suggestion()).matchRatio(score==null?null:guarded.matchRatio())
                .reuseKind(subjective?"SUBJECTIVE":"FRESH").build();
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
