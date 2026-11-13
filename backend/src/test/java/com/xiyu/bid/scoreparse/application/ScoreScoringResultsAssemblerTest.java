package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ScoreResultReusePolicy;
import com.xiyu.bid.scoreparse.entity.*;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ScoreScoringResultsAssemblerTest {
    @Test void zeroPartialAndFailedCountsAreExclusiveAndConserveCurrentItems() {
        var items=List.of(item(1,"OBJECTIVE"),item(2,"OBJECTIVE"),item(3,"OBJECTIVE"),item(4,"SUBJECTIVE"));
        var rows=Map.of(1L,row(1,BigDecimal.ZERO,"FRESH"),2L,row(2,new BigDecimal("3"),"FRESH"),
                3L,row(3,null,"FAILED"),4L,row(4,null,"SUBJECTIVE"));
        var result=new ScoreScoringResultsAssembler().assemble(items,rows,null,
                Map.of(1L,source("SCORED"),2L,source("SCORED"),3L,source("FAILED"),4L,source("SUBJECTIVE")));
        assertThat(result.qualityStatus()).isEqualTo("PARTIAL");
        assertThat(result.objectiveScored()).isEqualTo(2);assertThat(result.failed()).isEqualTo(1);
        assertThat(result.subjectivePending()).isEqualTo(1);assertThat(result.objectivePending()).isZero();
        assertThat(result.objectiveScored()+result.objectivePending()+result.subjectivePending()+result.failed()).isEqualTo(items.size());
        assertThat(result.summary().totalActualScore()).isEqualByComparingTo("3");
    }
    @Test void missingDiagnosticsIsUnknownRatherThanKnownZeroFailures() {
        var result=new ScoreScoringResultsAssembler().assemble(List.of(item(1,"OBJECTIVE")),Map.of());
        assertThat(result.diagnosticsKnown()).isFalse();assertThat(result.qualityStatus()).isEqualTo("UNKNOWN");
    }
    @Test void expiredSourceNeverReintroducesPriorActualScoreIntoTotal() {
        var result=new ScoreScoringResultsAssembler().assemble(List.of(item(1,"OBJECTIVE")),
                Map.of(1L,row(1,null,"SOURCE_EXPIRED")),null,Map.of(1L,source("SCORED")));
        assertThat(result.objectivePending()).isEqualTo(1);assertThat(result.summary().totalActualScore()).isEqualByComparingTo("0");
        assertThat(result.results().getFirst().reasonCode()).isEqualTo("SOURCE_DOCUMENT_CHANGED");
    }
    private ScoreItem item(long id,String type){return ScoreItem.builder().id(id).weight(BigDecimal.TEN).scoreType(type).build();}
    private ScoreResult row(long id,BigDecimal score,String kind){return ScoreResult.builder().scoreItemId(id).actualScore(score).reuseKind(kind).build();}
    private ScoreResultReusePolicy.Source source(String outcome){return new ScoreResultReusePolicy.Source(
            new ScoreResultReusePolicy.Identity("file","rule","actual-score-v2","policy"),"COMPLETE",outcome,1L);}
}
