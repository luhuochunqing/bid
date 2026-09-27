package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceRecheckPlanTest {
    @Test void retriesOnlyUnresolvedAnchorAndRetainsSuccessfulCandidate() {
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc", "sha", "PDF", "v2"),
                List.of(table("a", "服务甲（2分）最高2分"), table("b", "服务乙（3分）最高3分")), List.of());
        var first = fact("甲", "a", "a-cell", "服务甲（2分）最高2分",2);
        var plan = SourceRecheckPlan.build(artifact, List.of(first), List.of());
        assertThat(plan.artifact().tables()).extracting(DocumentArtifact.CanonicalTable::tableId).containsExactly("b");
        assertThat(plan.text()).contains("服务乙").doesNotContain("服务甲");
        var second = fact("乙", "b", "b-cell", "服务乙（3分）最高3分",3);
        assertThat(SourceRecheckPlan.merge(List.of(first), List.of(second), List.of(OriginalScoreEvidencePolicy.modelFactId(second)), List.of()))
                .containsExactly(first,second);
    }
    @Test void repeatedCandidateIdentityNeverReplacesAnArbitraryInitialFact() {
        var first=fact("甲","a","a-cell","原文甲",2);
        var other=fact("甲","b","b-cell","原文乙",3);
        var retried=fact("甲","c","c-cell","重试",4);
        assertThat(SourceRecheckPlan.merge(List.of(first,other),List.of(retried),List.of(OriginalScoreEvidencePolicy.modelFactId(retried)),List.of()))
                .contains(first,other);
    }
    @Test void boundedRetryReportsAllUnselectedSources() {
        var tables = java.util.stream.IntStream.range(0, 10).mapToObj(i -> table("t"+i,"评分项（2分）最高2分")).toList();
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","sha","PDF","v2"), tables,List.of());
        var plan = SourceRecheckPlan.build(artifact,List.of(),List.of());
        assertThat(plan.artifact().tables()).hasSize(8);
        assertThat(plan.diagnostics()).containsExactly("SOURCE_RECHECK_LIMIT_REMAINING:2");
        assertThat(plan.text()).hasSizeLessThanOrEqualTo(12000);
    }

    @Test void selectedOcrBlockKeepsAnchorBoundsAndDocumentOnRetry() {
        var bounds = new DocumentArtifact.BoundingBox(1,2,20,30,100,100);
        var block = new DocumentArtifact.TextBlockEvidence("ocr",2,"评分办法","服务（3分）最高3分",99d,bounds);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("scan","sha","PDF","v2"),
                List.of(),List.of(),List.of(block),"2.2");
        var plan = SourceRecheckPlan.build(artifact,List.of(),List.of());
        assertThat(plan.artifact().textBlocks()).containsExactly(block);
        var anchor = SourceAnchorCatalog.build(plan.artifact()).getFirst();
        assertThat(anchor.sourceSpans().getFirst().locator()).isEqualTo(block.locator());
        assertThat(anchor.sourceSpans().getFirst().documentId()).isEqualTo("scan");
        var restored = SourceAnchorCatalog.restore(fact("服务","",anchor.id(),"错字",3),List.of(anchor));
        assertThat(restored.quotedText()).isEqualTo(block.text());
        assertThat(restored.sourceMetadata().sourceSpans().getFirst().pageNumber()).isEqualTo(2);
    }

    @Test void successfulSameNameFactSurvivesRetryFromOtherDocumentOrRow() {
        for (String source : List.of("doc1-row", "doc0-other-row")) {
            var first=fact("甲","doc0-row","doc0-cell","原文甲",2);
            var retried=fact("甲",source,source+"-cell","新来源",3);
            assertThat(SourceRecheckPlan.merge(List.of(first),List.of(retried),
                    List.of(OriginalScoreEvidencePolicy.modelFactId(retried)),List.of()))
                    .containsExactly(first,retried);
        }
    }

    @Test void onlyTheSelectedFailedSourceCanBeReplacedWhileSameNameSuccessSurvives() {
        var failed = fact("甲","a","a-cell","错误引文",9);
        var successful = fact("甲","b","b-cell","服务甲（3分）最高3分",3);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","sha","PDF","v2"),
                List.of(table("a","服务甲（2分）最高2分"),table("b","服务甲（3分）最高3分")),List.of());
        var violation = new MultimodalScoreFactValidator.Violation(OriginalScoreEvidencePolicy.modelFactId(failed),
                "quotedText","SOURCE_QUOTE_NOT_VERIFIED","bad", "source", "bad", "a-cell");
        var plan=SourceRecheckPlan.build(artifact,List.of(failed,successful),List.of(violation));
        var repaired=fact("甲","a","a-cell","服务甲（2分）最高2分",2);
        var merged=plan.merge(List.of(failed,successful),List.of(repaired),List.of(OriginalScoreEvidencePolicy.modelFactId(repaired)));
        assertThat(merged).hasSize(2).contains(successful);
        assertThat(merged.getFirst().sourceCellIds()).containsExactly("a-cell");
        assertThat(merged.getFirst().weight()).isEqualByComparingTo("2");
        assertThat(merged.getFirst().sourceMetadata().diagnostics()).contains("SOURCE_RECHECK_REPLACED:错误引文");
        var alien=fact("甲","other","other-cell","第三个来源",4);
        assertThat(plan.merge(List.of(failed,successful),List.of(alien),List.of(OriginalScoreEvidencePolicy.modelFactId(alien))))
                .containsExactly(failed,successful,alien);
    }

    private static DocumentArtifact.CanonicalTable table(String id,String raw) {
        return new DocumentArtifact.CanonicalTable(id,1,"评分办法",1,1,List.of(),List.of(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(id+"-cell",0,0,raw,raw,1,1,false,"pdf:page=1",1)),true);
    }
    private static MultimodalScoreFact fact(String code,String table,String cell,String quote,int weight) {
        return new MultimodalScoreFact("评分办法",code,"",code,BigDecimal.valueOf(weight),quote,"OBJECTIVE",1,
                table,List.of(cell),quote,.9,ChildAggregation.SUM);
    }
}
