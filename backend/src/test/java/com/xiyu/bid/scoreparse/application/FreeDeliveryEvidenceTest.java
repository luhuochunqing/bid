package com.xiyu.bid.scoreparse.application;
import com.xiyu.bid.biddraftagent.infrastructure.openai.*;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.entity.*;
import com.xiyu.bid.scoreparse.infrastructure.openai.*;
import java.math.BigDecimal;import java.time.Duration;import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class FreeDeliveryEvidenceTest {
 private static final String RULE="1）不受订单金额、配送范围限制全部免运费配送，得10分；2）免费配送订单金额为每批次订单金额最低者8分，其它按低至高顺序递减2分，最低得0分。";
 private static final String QUOTE="针对所有订单无最低起订金额限制、无配送区域限制，全程免费配送";
 @Test void completeUnconditionalHighestBandCanSurviveUnrelatedEmptyPhoto() {
  assertThat(assess(RULE,QUOTE,10).getActualScore()).isEqualByComparingTo("10");
 }
 @ParameterizedTest @ValueSource(strings={"0元起送", "所有订单无最低起订金额限制", "无配送区域限制，全程免费配送", "不承诺针对所有订单无最低起订金额限制、无配送区域限制，全程免费配送"})
 void incompleteOrNegatedPromiseCannotPublish(String quote) {assertThat(assess(RULE,quote,10).getActualScore()).isNull();}
 @Test void extraRuleConditionsOrNonTopScoreCannotPublish() {
  assertThat(assess(RULE+"另须提供盖章承诺函",QUOTE,10).getActualScore()).isNull();
  assertThat(assess(RULE,QUOTE,8).getActualScore()).isNull();
 }
 private ScoreResult assess(String rule,String quote,double raw) {
  var document=new ScoringDocument("[第 1 页]\n"+quote,List.of(Map.of("pageNumber",1)),new ScoringExtractionReport(1,"synthetic","COMPLETE",2,2,2,0,0,1,List.of(),List.of(),List.of(Map.of("pageNumber",2)),0));
  var analyzer=new OpenAiScoreAnalyzer(null,null,null) {
   @Override public ScoreAssessmentOutput assessObjective(String d,BigDecimal w,String excerpt,List<DocumentImage> images,OpenAiRequestBudget budget){var out=new ScoreAssessmentOutput();out.actualScore=raw;out.quote=quote;return out;}
  };
  long deadline=System.currentTimeMillis()+10000;
  try(var file=new ScoringFileSession(1L,"synthetic",new LoadedBidFile.InMemory(new byte[]{1},"synthetic.pdf","pdf"),null,null,deadline,()->{})) {
   return new ScoreItemAssessor(analyzer,new ImageBudget(0,0)).assessScoring(ScoreParseTask.builder().id(1L).build(),ScoreItem.builder().id(2L).dim("免费配送最小起订额度").detail(rule).weight(BigDecimal.TEN).scoreType("OBJECTIVE").build(),document,file,deadline,new OpenAiBidAgentRequestConfig("synthetic","http://provider.invalid","synthetic",Duration.ofSeconds(1),OpenAiBidAgentApiStyle.CHAT_COMPLETIONS));
  }
 }
}
