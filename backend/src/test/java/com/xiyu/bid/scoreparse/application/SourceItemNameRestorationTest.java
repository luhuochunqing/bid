package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic source only: a 40-point parent with two children and a standalone 60-point item. */
class SourceItemNameRestorationTest {
    @Test void restoresParentAndChildrenWithoutChangingRulesOrDoubleCounting() {
        var source = source(false);
        var facts = TextPrimaryEvidenceBinder.bind(List.of(fact("1", "", 40, "1", "服务能力（40分）"),
                fact("1.1", "1", 15, "1", "1.提供服务网点得15分。"),
                fact("1.2", "1", 25, "1", "2.提供人员方案得25分。"),
                fact("2", "", 60, "2", "履约能力（60分）")), source);
        assertThat(facts).extracting(MultimodalScoreFact::dimension)
                .containsExactly("服务能力", "服务能力", "服务能力", "履约能力");
        assertThat(facts).extracting(MultimodalScoreFact::parentCode).containsExactly("", "1", "1", "");
        assertThat(facts).extracting(MultimodalScoreFact::weight)
                .containsExactly(new BigDecimal(40), new BigDecimal(15), new BigDecimal(25), new BigDecimal(60));
        var report = new MultimodalScoreFactValidator().validate(facts, source, new BigDecimal(100));
        var mapped = facts.stream().map(f -> TextPrimaryScoreFactMapper.map(f, source, report)).toList();
        assertThat(mapped.get(1).sourceText()).isEqualTo("1.提供服务网点得15分。");
        assertThat(mapped.get(2).sourceText()).isEqualTo("2.提供人员方案得25分。");
        assertThat(mapped.get(3).sourceText()).isEqualTo("提供合同得60分，无合同不得分。");
        assertThat(ScoreUnitPolicy.totalWeight(mapped.stream().map(f -> new ScoreUnitPolicy.Node(
                f.factId(), f.parentFactId(), f.weight(), f.childAggregation().name())).toList())).isEqualByComparingTo("100");
    }
    @Test void ambiguousNameColumnsMustNotGuess() {
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(fact("1", "", 40, "1", "服务能力（40分）")), source(true))
                .getFirst().dimension()).isEqualTo("详细评审");
    }
    @Test void sourceCellsFromDifferentRowsMustNotGuess() {
        var f = fact("1", "", 40, "1", "服务能力（40分）");
        f = new MultimodalScoreFact(f.sectionTitle(), f.code(), f.parentCode(), f.dimension(), f.weight(), f.ruleText(),
                f.scoreType(), 1, "t", List.of("t-r1-c1", "t-r2-c1"), f.quotedText(), .9);
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(f), source(false)).getFirst().dimension()).isEqualTo("详细评审");
    }
    @Test void excludesOutOfSchemeAndPreservesSpecificModelNames() {
        var f=fact("1", "", 40, "1", "服务能力（40分）");
        var outside=f.withScope("other", "OUT_OF_SCHEME");
        assertThat(SourceItemNameRestoration.restore(List.of(outside), source(false)).getFirst().dimension())
                .isEqualTo("详细评审");
        var specific=new MultimodalScoreFact(f.sectionTitle(),f.code(),f.parentCode(),"其他具体项目",f.weight(),
                f.ruleText(),f.scoreType(),f.pageNumber(),f.tableId(),f.sourceCellIds(),f.quotedText(),.9);
        assertThat(SourceItemNameRestoration.restore(List.of(specific), source(false)).getFirst().dimension())
                .isEqualTo("其他具体项目");
    }

    @Test void anchoredSourceAlsoRestoresNameAndKeepsModelClaim() {
        var source=source(false); var f=fact("2", "", 60, "2", "履约能力（60分）");
        var metadata=new ScoreSourceMetadata(f.ruleText(),f.quotedText(),SourceSpanResolver.resolve(source,f.sourceCellIds()),
                List.of(),"详细评审",f.scoreType(),ModelSourceClaim.from(f));
        var anchored=new MultimodalScoreFact(f.sectionTitle(),f.code(),f.parentCode(),f.dimension(),f.weight(),f.ruleText(),
                f.scoreType(),f.pageNumber(),f.tableId(),f.sourceCellIds(),f.quotedText(),.9,f.childAggregation(),"","",metadata);
        var result=TextPrimaryEvidenceBinder.bind(List.of(anchored),source).getFirst();
        assertThat(result.dimension()).isEqualTo("履约能力");
        assertThat(result.sourceMetadata().modelCategory()).isEqualTo("详细评审");
        assertThat(result.sourceMetadata().modelQuotedText()).isEqualTo(f.quotedText());
        assertThat(result.sourceMetadata().diagnostics()).contains("MODEL_DIMENSION:详细评审");
    }

    @Test void unanchoredTextFactAndContinuationRowRestoreNameFromLogicalRow() {
        var base=source(false);
        var table=base.tables().getFirst();
        var cells=new ArrayList<>(table.cells());
        for (int c=0;c<4;c++) {
            String text=c==2 ? "3.提供人员培训得10分。" : "";
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                    "t-r3-c"+c,3,c,text,text,1,1,false,"pdf:page=2",2,"t-r1"));
        }
        var contSource=new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","synthetic","PDF","2.2"),
                List.of(new DocumentArtifact.CanonicalTable(
                table.tableId(),1,table.sourceSection(),4,4,List.of(),cells,true,"BAIDU_OCR")),List.of());
        var unanchored=new MultimodalScoreFact("第四章 评审办法","1.3","1","详细评审",new BigDecimal(10),
                "3.提供人员培训得10分。","OBJECTIVE",2,"",List.of(),"3.提供人员培训得10分。",.9,
                ChildAggregation.UNKNOWN,"","",ScoreSourceMetadata.EMPTY);
        var bound=TextPrimaryEvidenceBinder.bind(List.of(unanchored),contSource).getFirst();
        assertThat(bound.dimension()).isEqualTo("服务能力");
        assertThat(bound.tableId()).isEqualTo("t");
        assertThat(bound.sourceCellIds()).containsExactly("t-r3-c2");
    }

    static MultimodalScoreFact fact(String code, String parent, int weight, String row, String quote) {
        return new MultimodalScoreFact("第四章 评审办法", code, parent, "详细评审", new BigDecimal(weight), quote,
                "OBJECTIVE", 1, "t", List.of("t-r"+row+"-c1", "t-r"+row+"-c2"), quote, .9,
                code.equals("1") ? ChildAggregation.SUM : ChildAggregation.UNKNOWN, "", "", ScoreSourceMetadata.EMPTY);
    }
    static DocumentArtifact source(boolean ambiguous) {
        String[][] rows={{"序号", "评审因素", ambiguous ? "评分项" : "评审标准", "分值"},
                {"1", "服务能力（40分）", "1.提供服务网点得15分。\n2.提供人员方案得25分。", "40"},
                {"2", "履约能力（60分）", "提供合同得60分，无合同不得分。", "60"}};
        var cells=new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        for(int r=0;r<rows.length;r++) for(int c=0;c<4;c++) cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=1",1,"t-r"+r));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","synthetic","PDF","2.2"),
                List.of(new DocumentArtifact.CanonicalTable("t",1,"第四章 评审办法",3,4,List.of(),cells,true,"PDF")),List.of());
    }
}
