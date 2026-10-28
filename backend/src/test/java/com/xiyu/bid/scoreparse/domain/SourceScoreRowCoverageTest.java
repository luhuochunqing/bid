package com.xiyu.bid.scoreparse.domain;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class SourceScoreRowCoverageTest {
 @Test void omittedOriginalScoringRowMustBlockPublication() {
  var r=OriginalScoreEvidencePolicy.verifyModel(artifact(),"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45)),BigDecimal.valueOf(95));
  assertThat(r.problems()).anySatisfy(p->{assertThat(p.code()).isEqualTo("SOURCE_SCORE_ROW_UNCOVERED");assertThat(p.locator()).contains("t-r3");});
  assertThat(r.publishable()).isFalse();
 }
 @Test void allOriginalRowsCoveredDoNotReportOmission() {
  var r=OriginalScoreEvidencePolicy.verifyModel(artifact(),"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45),fact(3,5)),BigDecimal.valueOf(100));
  assertThat(r.problems()).noneMatch(p->p.code().equals("SOURCE_SCORE_ROW_UNCOVERED"));
 }
 @Test void synonymousNameHeaderStillDetectsOmittedScoreRow() {
  var original=artifact();var table=original.tables().getFirst();var cells=new ArrayList<>(table.cells());
  cells.set(1,new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r0-c1",0,1,"评审内容","评审内容",1,1,false,"pdf:page=1",1,"t-r0"));
  var altered=new DocumentArtifact(original.artifact(),List.of(new DocumentArtifact.CanonicalTable("t",1,"第四章 评审办法",4,4,List.of(),cells,true,"PDF")),List.of());
  var result=OriginalScoreEvidencePolicy.verifyModel(altered,"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45)),BigDecimal.valueOf(95));
  assertThat(result.problems()).anyMatch(p -> p.code().equals("SOURCE_SCORE_ROW_UNCOVERED") && p.locator().contains("t-r3"));
 }
 @Test void sourceUnverifiedClaimDoesNotCoverOriginalRow() {
  var fake=new MultimodalScoreFact("第四章 评审办法","3","","丙",BigDecimal.valueOf(5),"丙项得5分。","OBJECTIVE",1,"t",List.of("t-r3-c2"),"伪造的规则",.9,ChildAggregation.SUM);
  var r=OriginalScoreEvidencePolicy.verifyModel(artifact(),"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45),fake),BigDecimal.valueOf(100));
  assertThat(r.problems()).anyMatch(p->p.code().equals("SOURCE_SCORE_ROW_UNCOVERED") && p.locator().contains("t-r3"));
 }
 @Test void nameOnlyDoesNotProveTheScoringRuleWasExtracted() {
  var heading=new MultimodalScoreFact("第四章 评审办法","3","","丙",BigDecimal.valueOf(5),"丙","OBJECTIVE",1,"t",List.of("t-r3-c1"),"丙",.9,ChildAggregation.SUM);
  var result=OriginalScoreEvidencePolicy.verifyModel(artifact(),"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45),heading),BigDecimal.valueOf(100));
  assertThat(result.problems()).anyMatch(p -> p.code().equals("SOURCE_SCORE_ROW_UNCOVERED") && p.locator().contains("t-r3"));
 }
 @Test void zeroPointDeductionRowIsCoveredOnlyWhenVerified() {
  var base=artifact();var t=base.tables().getFirst();var cells=new ArrayList<>(t.cells());
  String[] values={"4","质量扣分","未通过验收扣分。","0"};
  for(int c=0;c<4;c++)cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r4-c"+c,4,c,values[c],values[c],1,1,false,"pdf:page=1",1,"t-r4"));
  var full=new DocumentArtifact(base.artifact(),List.of(new DocumentArtifact.CanonicalTable("t",1,"第四章 评审办法",5,4,List.of(),cells,true,"PDF")),List.of());
  var zero=new MultimodalScoreFact("第四章 评审办法","4","","质量扣分",BigDecimal.ZERO,"未通过验收扣分。","OBJECTIVE",1,"t",List.of("t-r4-c2"),"未通过验收扣分。",.9,ChildAggregation.SUM);
  var found=OriginalScoreEvidencePolicy.verifyModel(full,"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45),fact(3,5),zero),BigDecimal.valueOf(100));
  assertThat(found.problems()).noneMatch(p -> p.code().equals("SOURCE_SCORE_ROW_UNCOVERED"));
  var missing=OriginalScoreEvidencePolicy.verifyModel(full,"# 第四章 评审办法\n",List.of(fact(1,50),fact(2,45),fact(3,5)),BigDecimal.valueOf(100));
  assertThat(missing.problems()).anyMatch(p -> p.code().equals("SOURCE_SCORE_ROW_UNCOVERED") && p.locator().contains("t-r4"));
 }
 @Test void omittedNumericScoreRowIsIncludedInBoundedRetry() {
  var art=artifact(); var violation=new MultimodalScoreFactValidator.Violation("", "sourceRow", "SOURCE_SCORE_ROW_UNCOVERED", "missing", "row", "missing", "t:t-r3");
  var plan=SourceRecheckPlan.build(art,List.of(fact(1,50),fact(2,45)),List.of(violation));
  assertThat(plan.artifact().tables().getFirst().cells()).extracting(DocumentArtifact.CanonicalTable.CanonicalCellDto::cellId)
    .contains("t-r3-c0","t-r3-c1","t-r3-c2","t-r3-c3");
 }
 private static MultimodalScoreFact fact(int row,int weight) {
  String name=List.of("","甲","乙","丙").get(row),quote=row==3?"丙项依据年报综合评审。":name+"项得"+weight+"分。";
  return new MultimodalScoreFact("第四章 评审办法",Integer.toString(row),"",name,BigDecimal.valueOf(weight),quote,"OBJECTIVE",1,"t",List.of("t-r"+row+"-c2"),quote,.9,ChildAggregation.SUM);
 }
 private static DocumentArtifact artifact() {
  var cells=new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();String[][] rows={{"条款号","评审因素","评审标准","分值"},{"1","甲","甲项得50分。","50"},{"2","乙","乙项得45分。","45"},{"3","丙","丙项依据年报综合评审。","5"}};
  for(int r=0;r<rows.length;r++)for(int c=0;c<4;c++)cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=1",1,"t-r"+r));
  var t=new DocumentArtifact.CanonicalTable("t",1,"第四章 评审办法",4,4,List.of(),cells,true,"PDF");
  return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","hash","PDF","2.2"),List.of(t),List.of());
 }
}
