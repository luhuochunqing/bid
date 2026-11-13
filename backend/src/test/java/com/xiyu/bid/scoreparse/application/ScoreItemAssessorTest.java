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
        assertThat(result.getMissedReason()).contains("人工确认");
    }

    @Test void subjectiveItemDiscardsNumbersAndNeverCallsObjectiveAnalyzer() {
        var output=output(10d,QUOTE);output.matchRatio=100;output.suggestion="建议补充";
        when(analyzer.assessSubjective(anyString(),anyString(),any(OpenAiRequestBudget.class))).thenReturn(output);
        var result=assess(item("SUBJECTIVE"));
        assertThat(result.getActualScore()).isNull();
        assertThat(result.getMatchRatio()).isNull();
        assertThat(result.getReuseKind()).isEqualTo("SUBJECTIVE");
        assertThat(result.getSuggestion()).isEqualTo("建议补充");
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

    private ScoreResult assess(ScoreItem item) {
        long deadline=System.currentTimeMillis()+10000;
        var document=new ScoringDocument(BID_TEXT,List.of(),new ScoringExtractionReport(
                1,"fixture","COMPLETE",1,1,0,0,0,0,List.of(),List.of(),List.of(),0));
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
