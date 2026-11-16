package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic closed revenue bands, exercised through the actual assessor. */
class SalesRevenueEvidenceTest {
    private static final String RULE="平台工业品2024年销售额，30亿元（含）以上得15分，20（含）-30亿元得13分，10（含）-20亿元得11分，5（含）-10亿元得9分，1（含）-5亿元得7分，低于1亿元得0分。";
    private static final String QUOTE="本公司2024年平台工业品的销售额为68.25亿元";

    @Test void highestRevenueBandSurvivesUnrelatedEmptyImageWithoutClearingWarning() {
        assertThat(assess(RULE,QUOTE,QUOTE,15,"COMPLETE").getActualScore()).isEqualByComparingTo("15");
    }
    @ParameterizedTest @ValueSource(strings={"提供经审计财务报表后，", "集团合并口径：", ""})
    void incompleteRuleOrNonTopAmountDoesNotBypassCoverage(String prefix) {
        String quote=QUOTE.replace("68.25","25");
        assertThat(assess(prefix+RULE,quote,quote,13,"COMPLETE").getActualScore()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={
        "本公司2023年平台工业品的销售额为68.25亿元", "本公司2024年销售额为68.25亿元",
        "本公司2024年平台工业品的销售额为68.25万元", "68.25亿元", "本公司2024年平台工业品的销售额不超过68.25亿元",
        "预计本公司2024年平台工业品的销售额为68.25亿元", "集团2024年平台工业品的销售额为68.25亿元"})
    void rejectsWrongYearMetricUnitOrIncompleteQuote(String quote) {
        assertThat(assess(RULE,quote,quote,15,"COMPLETE").getActualScore()).isNull();
    }
    @Test void conflictingDeclarationOrPartialCoverageCannotProveTopBand() {
        assertThat(assess(RULE,QUOTE+"\n本公司2024年平台工业品的销售额为20亿元",QUOTE,15,"COMPLETE").getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE,QUOTE,15,"PARTIAL").getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE,QUOTE,14,"COMPLETE").getActualScore()).isNull();
        assertThat(assess(RULE+"另须提交证明材料",QUOTE,QUOTE,15,"COMPLETE").getActualScore()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"20（含）-31亿元得13分", "20（含）-30亿元得16分", "30（含）-30亿元得13分"})
    void malformedOrNonMonotoneBandsCannotPublish(String broken) {
        assertThat(assess(RULE.replace("20（含）-30亿元得13分",broken),QUOTE,QUOTE,15,"COMPLETE").getActualScore()).isNull();
    }

    private ScoreResult assess(String rule,String text,String quote,double raw,String coverage) {
        var report=new ScoringExtractionReport(1,"synthetic",coverage,3,"COMPLETE".equals(coverage)?3:2,1,
                "COMPLETE".equals(coverage)?0:1,0,1,
                "COMPLETE".equals(coverage)?List.of():List.of(Map.of("pageNumber",3)),List.of(),List.of(Map.of("pageNumber",2)),0);
        var document=new ScoringDocument("[第 1 页]\n"+text,List.of(Map.of("pageNumber",1)),report);
        var analyzer=new OpenAiScoreAnalyzer(null,null,null) {
            @Override public ScoreAssessmentOutput assessObjective(String detail,BigDecimal weight,String excerpt,
                    List<DocumentImage> images,OpenAiRequestBudget budget) {
                var out=new ScoreAssessmentOutput();out.actualScore=raw;out.quote=quote;return out;
            }
        };
        long deadline=System.currentTimeMillis()+10000;
        try(var file=new ScoringFileSession(1L,"synthetic",new LoadedBidFile.InMemory(new byte[]{1},"synthetic.pdf","application/pdf"),null,null,deadline,()->{})) {
            return new ScoreItemAssessor(analyzer,new ImageBudget(0,0)).assessScoring(ScoreParseTask.builder().id(1L).build(),
                    ScoreItem.builder().id(2L).dim("销售额").detail(rule).weight(new BigDecimal("15")).scoreType("OBJECTIVE").build(),
                    document,file,deadline,new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
        }
    }
}
