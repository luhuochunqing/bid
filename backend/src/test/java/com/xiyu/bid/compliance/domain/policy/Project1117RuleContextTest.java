package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class Project1117RuleContextTest {
 @Test void quotedVetoIsNotMisconduct() {
  var r=SubItemAndAttachmentRules.checkVetoKeyword(doc("评标委员会应当否决其投标：（1）投标文件没有对招标文件的实质性要求和条件作出响应，或者对招标文件的偏差超出招标文件规定的偏差范围或最高项数；（2）有串通投标、弄虚作假、行贿等违法行为。"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.PASS);
 }
 @Test void actualMisconductAfterRuleStillFails() {
  var r=SubItemAndAttachmentRules.checkVetoKeyword(doc("评标委员会应当否决有串通投标的投标人。实际情况：我司已与其他投标人串通投标。"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.FAIL);
 }
 @Test void quotedNegativeDeviationIsNotActualDeviation() {
  var r=ResponsivenessReviewPolicy.check(doc("标注为重要指标，均为实质性要求。投标文件响应有负偏离的、属于投标文\n件存在重大偏差，评标委员会将否决其投标。"));
  assertThat(r==null||r.status()!=QualityCheckStatus.FAIL).isTrue();
 }
 @Test void actualNegativeDeviationAfterRuleStillFails() {
  var r=ResponsivenessReviewPolicy.check(doc("投标文件响应有负偏离的，评标委员会将否决其投标。我司本项目交货期存在负偏离，交货30天。"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.FAIL);
 }
 @Test void laterContactBodyIsNotHiddenByIndex() {
  var r=BasicInfoCheckPolicy.checkContactInfo(pages("评审索引表：需提供项目联系人及供货覆盖城市", "投标函\n投标人：某供应链公司\n联系人：张三\n电话：021-12345678\n地址：上海市某路1号"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.PASS);
  assertThat(r.evidences()).anyMatch(e->Integer.valueOf(2).equals(e.pageNumber()));
 }
 @Test void commitmentEvidenceLocatesBodyNotInstruction() {
  var r=SubItemAndAttachmentRules.checkCommitmentSeal(pages("评审索引表：承诺在5天内到货（提供承诺函），证明文件不足不得分。", "承诺函\n致采购人：我司承诺在5天内到货。特此承诺！\n投标人：某供应链公司（盖单位公章）\n2026年9月21日"));
  assertThat(r.evidences()).anyMatch(e->Integer.valueOf(2).equals(e.pageNumber()));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
 }
 @Test void negationInPreviousSentenceMustNotHideActualMisconduct() {
  var r=SubItemAndAttachmentRules.checkVetoKeyword(doc("我司不存在挂靠资质。我司已串通投标。"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.FAIL);
 }
 @Test void actualDeviationEvidenceMustNotPointAtEarlierRuleCitation() {
  String rule="投标文件响应有负偏离的，评标委员会将否决其投标。";
  var r=ResponsivenessReviewPolicy.check(doc(rule+"无关的招标引用内容".repeat(30)+"。我司本项目交货期存在负偏离，交货30天。"));
  assertThat(r.evidences()).anyMatch(e->e.quote().contains("我司本项目交货期存在负偏离"));
 }
 @Test void signerNameAndCaptionDoNotProveActualSeal() {
  var r=SubItemAndAttachmentRules.checkCommitmentSeal(doc("承诺书\n我司承诺履约。\n承诺人：张三（盖章）"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
 }
 @Test void realContactOnSamePageAsIndexMustNotBeDiscarded() {
  var r=BasicInfoCheckPolicy.checkContactInfo(doc("评审索引表：提供项目联系人。\n投标人联系信息\n联系人：张三 电话：021-12345678\n地址：某路1号"));
  assertThat(r.status()).isEqualTo(QualityCheckStatus.PASS);
 }
 private static DocumentContent doc(String s){return DocumentContent.fromText("sample.pdf","sample",s);}
 private static DocumentContent pages(String a,String b){return new DocumentContent("sample.pdf","sample",a+"\n"+b,List.of(new DocumentContent.PageContent(1,a,List.of()),new DocumentContent.PageContent(2,b,List.of())));}
}
