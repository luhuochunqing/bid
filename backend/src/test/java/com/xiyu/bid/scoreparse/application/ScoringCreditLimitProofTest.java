package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Synthetic amounts and the real native-row / COMPLETE-with-empty-units wire shape. */
class ScoringCreditLimitProofTest {
    private static final String RULE = "21 | 8 | 根据赊销额度X进行评审：\n"
            + "(1)X≥1800万元，得8分；\n(2)1800万元>X≥900万元，得5分；\n"
            + "(3)900万元>X≥300万元，得2分；\n(4)X<300万元，得0分。";
    private static final String QUOTE = "| 7 | 赊销额度 | 2600万元 |";

    private List<String> leadingParagraphs=List.of();
    private String sentExcerpt;
    @Test void longDocumentStillSendsItsBoundNativeResponseToTheModelWithinBudget()throws Exception {
        String filler="本节说明业务方案预算900万元及通用实施安排。项目小组记录交付流程、复核节点和质量检查事项。".repeat(30);
        leadingParagraphs=java.util.stream.IntStream.range(0,30).mapToObj(i->("第"+i+"节业务方案。"+filler).substring(0,900)).toList();
        assertThat(assess(RULE,QUOTE,8d,null,122,false,false).getActualScore()).isEqualByComparingTo("8");
        assertThat(sentExcerpt).contains(QUOTE);assertThat(sentExcerpt.length()).isLessThanOrEqualTo(12000);
    }
    @Test void highestBandSurvivesUnrelatedEmptyOcrWithACompleteNativeRow()throws Exception {
        var result=assess(RULE,QUOTE,8d,null,122,false,false);
        assertThat(result.getActualScore()).isEqualByComparingTo("8");
        assertThat(result.getQuote()).isEqualTo(QUOTE);
    }
    @Test void equivalentYuanAndHundredMillionUnitsAreComparedByServer()throws Exception {
        assertThat(assess(RULE,QUOTE.replace("2600万元","26000000元"),8d,null,122,false,false).getActualScore()).isEqualByComparingTo("8");
        assertThat(assess(RULE,QUOTE.replace("2600万元","0.26亿元"),8d,null,122,false,false).getActualScore()).isEqualByComparingTo("8");
    }
    @Test void modelMismatchCannotUseRatioOrCompleteCoverageToPublishWrongScore()throws Exception {
        assertThat(assess(RULE,QUOTE,7d,100,0,false,false).getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE,8d,0,0,false,false).getActualScore()).isNull();
    }
    @Test void incompleteEvidenceCannotProveLowerBandsButCleanCompleteKeepsLegalZeroAndPartial()throws Exception {
        assertThat(assess(RULE,QUOTE.replace("2600万元","1000万元"),5d,null,122,false,false).getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE.replace("2600万元","100万元"),0d,null,122,false,false).getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE.replace("2600万元","1000万元"),5d,null,0,false,false).getActualScore()).isEqualByComparingTo("5");
        assertThat(assess(RULE,QUOTE.replace("2600万元","100万元"),0d,null,0,false,false).getActualScore()).isEqualByComparingTo("0");
        assertThat(assess(RULE,null,8d,null,122,false,false).getActualScore()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={
            "根据累计赊销额度X进行评审：",
            "根据赊销额度X及财务比率进行评审：",
            "根据未提供赊销额度X进行评审："
    })
    void nonClosedMetricRulesRemainPending(String header)throws Exception {
        assertThat(assess(RULE.replace("根据赊销额度X进行评审：",header),QUOTE,8d,null,122,false,false).getActualScore()).isNull();
    }
    @Test void extraConditionCurrencyConflictOrAnotherAmountCannotBeIgnored()throws Exception {
        assertThat(assess(RULE+"须附银行证明并盖章。",QUOTE,8d,null,122,false,false).getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE.replace("万元","万美元"),8d,null,122,false,false).getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE,8d,null,122,true,false).getActualScore()).isNull();
        assertThat(assess(RULE,QUOTE,8d,null,122,false,true).getActualScore()).isNull();
    }

    private ScoreResult assess(String rule,String quote,Double modelScore,Integer ratio,int empty,
                               boolean conflictingRow,boolean brokenNativeBinding)throws Exception {
        String sourceRow=quote==null?QUOTE:quote;
        String[] cells=sourceRow.substring(1,sourceRow.length()-1).split("\\|",-1);
        List<Map<String,Object>> refs=new ArrayList<>(),emptyRefs=new ArrayList<>();
        for(int i=0;i<leadingParagraphs.size();i++)refs.add(Map.of("partName","word/document.xml","anchor","p"+(i+1),"kind","TEXT","text",leadingParagraphs.get(i)));
        for(int i=0;i<cells.length;i++)refs.add(Map.of("partName","word/document.xml","anchor","p"+(41+i+(brokenNativeBinding&&i==2?2:0)),"kind","TEXT","text",cells[i].trim()));
        StringBuilder markdown=new StringBuilder(leadingParagraphs.isEmpty()?"":String.join("\n\n",leadingParagraphs)+"\n\n").append(sourceRow);
        for(int i=0;i<empty;i++) {
            var ref=Map.<String,Object>of("partName","word/document.xml","anchor","p"+(100+i),"relationshipId","rId"+i,"kind","IMAGE","mediaName","word/media/image"+i+".png");
            refs.add(ref);emptyRefs.add(ref);markdown.append("\n\n[word/document.xml#p").append(100+i).append(" rId").append(i).append("]");
        }
        if(conflictingRow) {
            markdown.append("\n\n| 8 | 赊销额度 | 500万元 |");
            refs.add(Map.of("partName","word/document.xml","anchor","p300","kind","TEXT","text","8"));
            refs.add(Map.of("partName","word/document.xml","anchor","p301","kind","TEXT","text","赊销额度"));
            refs.add(Map.of("partName","word/document.xml","anchor","p302","kind","TEXT","text","500万元"));
        }
        Map<String,Object> coverage=new LinkedHashMap<>();
        coverage.put("schemaVersion",1);coverage.put("engineVersion","scoring-ocr-v2");coverage.put("coverageStatus","COMPLETE");
        coverage.put("sourceUnitCount",refs.size());coverage.put("processedUnitCount",refs.size());coverage.put("uniqueOcrUnitCount",empty);
        coverage.put("unreadUnitCount",0);coverage.put("failedUnitCount",0);coverage.put("emptyUnitCount",empty);
        coverage.put("unreadUnits",List.of());coverage.put("failedUnits",List.of());coverage.put("emptyUnits",emptyRefs);
        coverage.put("enumerationComplete",true);coverage.put("elapsedMillis",1);
        byte[] wire=new ObjectMapper().writeValueAsBytes(Map.of("markdown",markdown.toString(),"pages",List.of(),"sourceRefs",refs,"extraction",coverage));
        var document=new ScoringSidecarResponseReader().read(new ByteArrayInputStream(wire),100000,System.currentTimeMillis()+5000);
        var output=new ScoreAssessmentOutput();output.actualScore=modelScore;output.matchRatio=ratio;output.quote=quote;output.quoteMissing=quote==null;
        var analyzer=mock(OpenAiScoreAnalyzer.class);
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenAnswer(inv->{sentExcerpt=inv.getArgument(2);return output;});
        try(var file=new ScoringFileSession(1L,"synthetic",new LoadedBidFile.InMemory(new byte[]{1},"bid.docx","docx"),
                mock(SidecarStreamTextExtractor.class),null,System.currentTimeMillis()+5000,()->{})) {
            return new ScoreItemAssessor(analyzer,new ImageBudget(3,1024)).assessScoring(ScoreParseTask.builder().id(1L).build(),
                    ScoreItem.builder().id(1L).dim("结算能力").detail(rule).weight(new BigDecimal("8")).scoreType("OBJECTIVE").build(),
                    document,file,System.currentTimeMillis()+5000,new OpenAiBidAgentRequestConfig("synthetic","http://synthetic.invalid/v1","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
        }
    }
}
