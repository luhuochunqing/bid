package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.PageImage;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
class ScanPageTextPolicyTest {
 private static DocumentContent document() {
  return new DocumentContent("bid.pdf","hash","法定代表人身份证明\n11",List.of(
   new DocumentContent.PageContent(10,"法定代表人身份证明\n身份证复印件粘贴处",List.of()),
   new DocumentContent.PageContent(11,"11",List.of()),
   new DocumentContent.PageContent(90,"90",List.of())),List.of(
   new PageImage(1,"image/png","page",11,100,100,"aGk="),
   new PageImage(2,"image/png","page",90,100,100,"aGk=")));
 }
 @Test void sparsePageNextToIdentitySectionIsSelectedButUnrelatedPageIsNot() {
  assertThat(ScanPageTextPolicy.candidatePages(document())).containsExactly(11);
 }
 @Test void highConfidenceTranscriptionRestoresAuthorizationBodyAndPreservesHash() {
  var result=ScanPageTextPolicy.enrich(document(),List.of(new ScanPageTextPolicy.Transcription(11,
   "授权委托书\n法定代表人：张三，委托代理人：李四，授权期限：自签署之日至投标有效期结束，全权代表本公司提交文件。",0.95)));
  assertThat(result.fileHash()).isEqualTo("hash");
  assertThat(AuthorizationLetterCheckPolicy.check(result).evidences().getFirst().pageNumber()).isEqualTo(11);
  assertThat(result.pages().get(1).text()).contains("页面图像转写");
 }
 @Test void lowConfidenceUnrequestedOrConflictingTranscriptionsCannotAddFacts() {
  var result=ScanPageTextPolicy.enrich(document(),List.of(
    new ScanPageTextPolicy.Transcription(11,"不可靠识别",0.5),new ScanPageTextPolicy.Transcription(90,"非候选页",0.99)));
  assertThat(result.fullText()).isEqualTo(document().fullText());
 }

 @Test void transcribedIdentityNumbersAloneDoNotVerifyIdCardImages() {
  var doc=ScanPageTextPolicy.enrich(document(),List.of(new ScanPageTextPolicy.Transcription(11,
    "授权委托书 法定代表人身份证明 身份证号码：110101198001011234 委托代理人：李四 授权期限：投标有效期",0.95)));
  var result=SemanticEvidenceBoundary.reconcile(com.xiyu.bid.compliance.domain.QualityCheckItemResult.pass(
    com.xiyu.bid.compliance.domain.BidQualityCheckItem.ID_AUTH_LETTER,"识别到身份证号码",List.of()),doc);
  assertThat(result.status()).isEqualTo(com.xiyu.bid.compliance.domain.QualityCheckStatus.MANUAL_REVIEW);
 }
}
