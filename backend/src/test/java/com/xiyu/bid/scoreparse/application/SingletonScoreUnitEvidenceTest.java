package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SingletonScoreUnitEvidenceTest {
    private static final String RULE="提供完整运营材料得5分，未提供不得分。";
    @Test void sourceProvenSingleChildWithEqualMaximumCountsOnce() {
        assertThat(normalize(List.of(parent(5),child("7.1",5,"t-r1-c2",RULE))).getFirst().childAggregation())
                .isEqualTo(ChildAggregation.SUM);
    }
    @Test void differentMaximumRemainsUnknown() {
        assertThat(normalize(List.of(parent(5),child("7.1",4,"t-r1-c2",RULE))).getFirst().childAggregation())
                .isEqualTo(ChildAggregation.UNKNOWN);
    }
    @Test void multipleChildrenRemainUnknown() {
        assertThat(normalize(List.of(parent(5),child("7.1",5,"t-r1-c2",RULE),child("7.2",5,"t-r1-c2",RULE))).getFirst().childAggregation())
                .isEqualTo(ChildAggregation.UNKNOWN);
    }
    @Test void missingOrAlteredQuoteRemainsUnknown() {
        assertThat(normalize(List.of(parent(5),child("7.1",5,"t-r1-c2","提供完整运营材料...不得分。"))).getFirst().childAggregation())
                .isEqualTo(ChildAggregation.UNKNOWN);
    }
    @Test void childFromDifferentRowRemainsUnknown() {
        assertThat(normalize(List.of(parent(5),child("7.1",5,"t-r2-c2",RULE))).getFirst().childAggregation())
                .isEqualTo(ChildAggregation.UNKNOWN);
    }
    private static List<MultimodalScoreFact> normalize(List<MultimodalScoreFact> facts) {
        var art=artifact(); var context=OriginalScoringContext.resolve(art,"# 第四章 评审办法\n");
        return MultimodalExtractionReviewPolicy.normalized(new MultimodalScoreFactExtractor.ExtractionResult(null,facts,"test",0),
                context,art,"# 第四章 评审办法\n").facts();
    }
    private static MultimodalScoreFact parent(int weight){return new MultimodalScoreFact("第四章 评审办法","7","","商务",BigDecimal.valueOf(weight),"","OBJECTIVE",14,"t",List.of("t-r1-c1"),"运营情况（5分）",.9,ChildAggregation.UNKNOWN);}
    private static MultimodalScoreFact child(String code,int weight,String id,String quote){return new MultimodalScoreFact("第四章 评审办法",code,"7","商务",BigDecimal.valueOf(weight),"","OBJECTIVE",14,"t",List.of(id),quote,.9,ChildAggregation.UNKNOWN);}
    private static DocumentArtifact artifact(){
        var cells=new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows={{"条款号","评审因素","评审标准","分值"},{"7","运营情况（5分）",RULE,"5"},{"8","其他情况（5分）",RULE,"5"}};
        for(int r=0;r<rows.length;r++)for(int c=0;c<4;c++)cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=14",14,"t-r"+r));
        var t=new DocumentArtifact.CanonicalTable("t",13,"第四章 评审办法",3,4,List.of(),cells,true,"PDF");
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","hash","PDF","2.2"),List.of(t),List.of());
    }
}
