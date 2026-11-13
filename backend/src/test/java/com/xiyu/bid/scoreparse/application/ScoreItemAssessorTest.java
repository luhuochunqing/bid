package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentApiStyle;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiBidAgentRequestConfig;
import com.xiyu.bid.biddraftagent.infrastructure.openai.OpenAiRequestBudget;
import com.xiyu.bid.scoreparse.domain.ImageBudget;
import com.xiyu.bid.scoreparse.domain.ScoringDocument;
import com.xiyu.bid.scoreparse.domain.ScoringExtractionReport;
import com.xiyu.bid.scoreparse.entity.ScoreItem;
import com.xiyu.bid.scoreparse.entity.ScoreParseTask;
import com.xiyu.bid.scoreparse.entity.ScoreResult;
import com.xiyu.bid.scoreparse.infrastructure.openai.OpenAiScoreAnalyzer;
import com.xiyu.bid.scoreparse.infrastructure.openai.ScoreAssessmentOutput;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Current scoring contract: verified citations, subjective score suppression and shared retry budget. */
@ExtendWith(MockitoExtension.class)
class ScoreItemAssessorTest {
    private static final String BID_TEXT="第一章 资质证明：具备 CMMI 5 级认证证书。";
    private static final String QUOTE="具备 CMMI 5 级认证证书";
    @Mock private OpenAiScoreAnalyzer analyzer;
    private ScoreParseTask task;

    @BeforeEach void setUp() {
        task=ScoreParseTask.builder().id(5L).taskId("task-uuid").projectId(1L)
                .taskType("SCORING").status("PROCESSING").build();
    }

    @ParameterizedTest @ValueSource(strings={"第1页：具备 CMMI 5 级认证证书", "具备 CMMI 5 级认\n证证书"})
    void layoutAndPageLocatorRestoreVerbatimSource(String quote) {
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output(8d,quote));
        var result=assess(item("OBJECTIVE"));
        assertThat(result.getActualScore()).isEqualByComparingTo("8");
        assertThat(result.getQuote()).isEqualTo(QUOTE);
    }
    @Test void verifiedSourceWinsOverContradictoryMissingFlag() {
        var output=output(8d,QUOTE);output.quoteMissing=true;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        assertThat(assess(item("OBJECTIVE")).getActualScore()).isEqualByComparingTo("8");
    }
    @Test void missingQuoteHasDistinctDiagnosticAndDoesNotTrustNarrative() {
        var output=output(10d,null);output.evidence="证据充分，完全符合";
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var result=assess(item("OBJECTIVE"));
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getMissedReason()).contains("MODEL_QUOTE_MISSING");
    }
    @Test void completeClosedCertificateAbsenceCanPublishZeroWithoutQuote() {
        var output=output(0d,null);output.quoteMissing=true;output.matchRatio=0;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var certificate=item("OBJECTIVE");certificate.setDetail("具有ISO9001质量管理体系认证证书得10分");
        var result=assess(certificate);
        assertThat(result.getActualScore()).isEqualByComparingTo("0");
        assertThat(result.getQuote()).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"存在证书","提取不完整","空单元","未知规则","附加材料"})
    void absenceZeroCannotBypassEvidenceAndRuleBoundaries(String boundary) {
        var output=output(0d,null);output.quoteMissing=true;output.matchRatio=0;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var certificate=item("OBJECTIVE");certificate.setDetail("具有ISO9001质量管理体系认证证书得10分");
        if("未知规则".equals(boundary))certificate.setDetail("提供有效OMS软件著作权登记证书得10分");
        if("附加材料".equals(boundary))certificate.setDetail(certificate.getDetail()+"。证明材料：投标人须提供认证证书扫描件");
        String text="存在证书".equals(boundary)?"已提供ISO9001质量管理体系认证证书":BID_TEXT;
        var result=assess(certificate,text,"提取不完整".equals(boundary)?"PARTIAL":"COMPLETE","空单元".equals(boundary)?1:0);
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getMissedReason()).contains("人工确认");
    }
    @Test void verifiedObjectiveTextPublishesWithoutImageTransmission() {
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output(8d,QUOTE));
        var result=assess(item("OBJECTIVE"));
        assertThat(result.getActualScore()).isEqualByComparingTo("8");
        assertThat(result.getQuote()).isEqualTo(QUOTE);
        assertThat(result.getReuseKind()).isEqualTo("FRESH");
        verify(analyzer).assessObjective(anyString(),any(),eq(BID_TEXT),eq(List.of()),any(OpenAiRequestBudget.class));
        verifyNoMoreInteractions(analyzer);
    }

    @ParameterizedTest @ValueSource(doubles={-1d,11d})
    void outOfRangeObjectiveScoreStaysPending(double value) {
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output(value,QUOTE));
        var result=assess(item("OBJECTIVE"));
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getStatusStage2()).isEqualTo("PENDING");
        assertThat(result.getMatchRatio()).isNull();
    }

    @Test void unverifiedCitationCannotPublishNumericScore() {
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output(10d,"投标文件没有出现的虚构证书"));
        var result=assess(item("OBJECTIVE"));
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getQuote()).isNull();
        assertThat(result.getMissedReason()).contains("人工确认", "QUOTE_NOT_IN_EXCERPT");
    }

    @Test void subjectiveItemDiscardsNumbersAndNeverCallsObjectiveAnalyzer() {
        var output=output(10d,QUOTE);output.matchRatio=100;output.suggestion="建议补充";
        when(analyzer.assessSubjective(anyString(),anyString(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var result=assess(item("SUBJECTIVE"));
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getMatchRatio()).isNull();
        assertThat(result.getReuseKind()).isEqualTo("SUBJECTIVE");
        assertThat(result.getSuggestion()).isEqualTo("建议补充");
        assertThat(result.getQuote()).isNull();
        verify(analyzer).assessSubjective(anyString(),eq(BID_TEXT),any(OpenAiRequestBudget.class));
        verifyNoMoreInteractions(analyzer);
    }

    @Test void unknownWeightDoesNotInvokeAnalyzer() {
        var item=item("OBJECTIVE");item.setWeight(null);
        var result=assess(item);
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getMissedReason()).contains("权重未知");
        verifyNoInteractions(analyzer);
    }

    @Test void consumedTransportFailureRetriesWithSameBudgetAndPublishesVerifiedText() {
        var calls=new AtomicInteger();var shared=new AtomicReference<OpenAiRequestBudget>();
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenAnswer(invocation->{
                    OpenAiRequestBudget budget=invocation.getArgument(4);
                    if(shared.get()==null)shared.set(budget);else assertThat(budget).isSameAs(shared.get());
                    budget.acquire(Duration.ofSeconds(1));
                    if(calls.incrementAndGet()==1)throw new IllegalStateException("temporary transport failure");
                    return output(8d,QUOTE);
                });
        assertThat(assess(item("OBJECTIVE")).getActualScore()).isEqualByComparingTo("8");
        assertThat(shared.get().attempts()).isEqualTo(2);
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void repeatedTransportFailureStopsAfterTwoTransmissions() {
        var calls=new AtomicInteger();
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenAnswer(invocation->{
                    OpenAiRequestBudget budget=invocation.getArgument(4);budget.acquire(Duration.ofSeconds(1));
                    calls.incrementAndGet();throw new IllegalStateException("transport unavailable");
                });
        assertThatThrownBy(()->assess(item("OBJECTIVE"))).isInstanceOf(IllegalStateException.class)
                .hasMessage("transport unavailable");
        assertThat(calls.get()).isEqualTo(2);
    }

    @Test void failureBeforeTransmissionIsNotRetried() {
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenThrow(new IllegalStateException("invalid configuration"));
        assertThatThrownBy(()->assess(item("OBJECTIVE"))).hasMessage("invalid configuration");
        verify(analyzer).assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class));
        verifyNoMoreInteractions(analyzer);
    }

    @Test void declaredMaximumCapsScoreAndSatisfiesStatus() {
        var item=item("OBJECTIVE");
        item.setDetail("提供一项有效证明可加1分，满分5分。");
        var output=output(10d,QUOTE);output.matchRatio=100;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output);
        var result=assess(item);
        assertThat(result.getActualScore()).isEqualByComparingTo("5");
        assertThat(result.getStatusStage2()).isEqualTo("OK");
    }

    @Test void partialRatioUnderDeclaredMaximumStaysPending() {
        var item=item("OBJECTIVE");
        item.setDetail("提供一项有效证明可加1分，满分5分。");
        var output=output(10d,QUOTE);output.matchRatio=60;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output);
        var result=assess(item);
        assertThat(result.getActualScore()).isEqualByComparingTo("3");
        assertThat(result.getStatusStage2()).isEqualTo("PENDING");
    }

    @Test void subItemMaximumDoesNotCapWholeItem() {
        var item=item("OBJECTIVE");
        item.setWeight(new BigDecimal("25"));
        item.setDetail("1) 提供指定六大地区各一个配送仓库，得15分；2) 每多一个仓库得1分，满分5分；3) 按仓储面积排序，第一名得5分。");
        var output=output(25d,QUOTE);output.matchRatio=100;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output);
        var result=assess(item);
        assertThat(result.getActualScore()).isEqualByComparingTo("25");
        assertThat(result.getStatusStage2()).isEqualTo("OK");
    }

    @ParameterizedTest
    @CsvSource(value={"NULL,10,OK", "100,10,OK", "50,5,PENDING"}, nullValues="NULL")
    void equalSubItemCapsPreserveWholeItemWeight(Integer ratio,String expectedScore,String expectedStatus) {
        var item=item("OBJECTIVE");
        item.setDetail("1）每提供一份业绩得1分，最高5分；2）每提供一名工程师得1分，最高5分。");
        var output=output(10d,QUOTE);output.matchRatio=ratio;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output);
        var result=assess(item);
        assertThat(result.getActualScore()).isEqualByComparingTo(expectedScore);
        assertThat(result.getStatusStage2()).isEqualTo(expectedStatus);
    }

    @ParameterizedTest
    @CsvSource(value={"NULL,7,7,OK", "100,7,7,OK", "50,7,4,PENDING", "NULL,5,5,PENDING"}, nullValues="NULL")
    void singleSubItemCapPreservesWholeItemWeight(Integer ratio,double rawScore,String expectedScore,String expectedStatus) {
        var item=item("OBJECTIVE");
        item.setWeight(new BigDecimal("7"));
        item.setDetail("1）提供体系认证得2分；2）每提供一份业绩得1分，最高5分。");
        var output=output(rawScore,QUOTE);output.matchRatio=ratio;
        when(analyzer.assessObjective(anyString(),any(),anyString(),anyList(),any(OpenAiRequestBudget.class)))
                .thenReturn(output);
        var result=assess(item);
        assertThat(result.getActualScore()).isEqualByComparingTo(expectedScore);
        assertThat(result.getStatusStage2()).isEqualTo(expectedStatus);
    }

    private ScoreResult assess(ScoreItem item) {return assess(item,BID_TEXT,"COMPLETE",0);}
    private ScoreResult assess(ScoreItem item,String text,String coverage,int emptyCount) {
        long deadline=System.currentTimeMillis()+10000;
        int unread="PARTIAL".equals(coverage)?1:0;
        var document=new ScoringDocument(text,List.of(),new ScoringExtractionReport(
                1,"fixture",coverage,1,1-unread,0,unread,0,emptyCount,
                unread==0?List.of():List.of(java.util.Map.of("pageNumber",1)),List.of(),
                emptyCount==0?List.of():List.of(java.util.Map.of("pageNumber",1)),0));
        try(var session=new ScoringFileSession(1L,"hash",new LoadedBidFile.InMemory(new byte[]{1},"bid.docx","docx"),
                mock(SidecarStreamTextExtractor.class),null,deadline,()->{})) {
            return new ScoreItemAssessor(analyzer,new ImageBudget(3,1024)).assessScoring(task,item,document,session,deadline,
                    new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid/v1","synthetic",Duration.ofSeconds(1),
                            OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
        }
    }
    private static ScoreItem item(String type) {
        return ScoreItem.builder().id(10L).dim("资质").detail("具备 CMMI 5 级认证证书")
                .weight(BigDecimal.TEN).scoreType(type).build();
    }
    private static ScoreAssessmentOutput output(Double score,String quote) {
        var output=new ScoreAssessmentOutput();output.actualScore=score;output.quote=quote;return output;
    }
}
