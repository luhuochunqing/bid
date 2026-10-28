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
class ZeroDeductionUnitEvidenceTest {
 private static final String ONE="1.质量问题按标准扣分。", TWO="2.超过限制时暂停交易权限。";
 @Test void completeNumberedDeductionChildrenArePreserved() {assertThat(normalize(ONE,TWO,0)).isEqualTo(ChildAggregation.SUM);}
 @Test void incompleteDeductionStaysUnknown() {assertThat(normalize(ONE,"2.超过限制时暂停。",0)).isEqualTo(ChildAggregation.UNKNOWN);}
 @Test void positiveScoreIsNotTreatedAsZeroDeduction() {assertThat(normalize(ONE,TWO,5)).isEqualTo(ChildAggregation.UNKNOWN);}
 private static ChildAggregation normalize(String one,String two,int score){
  var cells=new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();String[][] rows={{"条款号","评审因素","评审标准","分值"},{"10","质量问题扣分说明",ONE+" "+TWO,"0"}};
  for(int r=0;r<2;r++)for(int c=0;c<4;c++)cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=14",14,"t-r"+r));
  var t=new DocumentArtifact.CanonicalTable("t",13,"第四章 评审办法",2,4,List.of(),cells,true,"PDF");var art=new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","hash","PDF","2.2"),List.of(t),List.of());
  var p=f("10","",score,"t-r1-c1","质量问题扣分说明");var a=f("10.1","10",0,"t-r1-c2",one);var b=f("10.2","10",0,"t-r1-c2",two);
  var x=new MultimodalScoreFactExtractor.ExtractionResult(null,List.of(p,a,b),"test",0);
  return MultimodalExtractionReviewPolicy.normalized(x,OriginalScoringContext.resolve(art,"# 第四章 评审办法\n"),art,"# 第四章 评审办法\n").facts().getFirst().childAggregation();
 }
 private static MultimodalScoreFact f(String code,String parent,int score,String id,String quote){return new MultimodalScoreFact("第四章 评审办法",code,parent,"综合",BigDecimal.valueOf(score),"","OBJECTIVE",14,"t",List.of(id),quote,.9,ChildAggregation.UNKNOWN);}
}
