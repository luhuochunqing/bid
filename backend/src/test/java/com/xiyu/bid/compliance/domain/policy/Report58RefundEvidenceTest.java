package com.xiyu.bid.compliance.domain.policy;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
class Report58RefundEvidenceTest {
 @Test void refundInstructionWithoutBankFactsMustNotPass() {
  var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash","保证金退还：中标后按平台规定办理。"),"投标人须提交保证金退款账户信息表");
  assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
 }
 @Test void bankFactsInRefundBodyPass() {
  var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash","保证金退款信息表\n户名：甲乙物资有限公司\n开户银行：中国银行上海分行\n银行账号：1234567890123456"),"投标人须提交保证金退款账户信息表");
  assertThat(r.status()).isEqualTo(QualityCheckStatus.PASS);
 }
 @Test void unknownRequirementDoesNotInventMissingMaterial() {
  var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash","普通正文"),"保证金按平台流程退还");
  assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
  assertThat(r.conclusion()).contains("适用");
 }

 @Test void placeholdersCannotProveRefundBankFacts() {
  for (String bank : java.util.List.of("请填写","__","【请填写】")) {
   var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash",
     "保证金退款信息表\n户名：请填写\n开户银行："+bank+"\n银行账号：1234567890123456"),"须提交退款信息表");
   assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
  }
 }

 @Test void genericHolderMarkersCannotCountAsCompanyName() {
  for (String holder : java.util.List.of("【此处填写】", "[输入内容]", "____", "此处填写公司")) {
   var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash",
    "保证金退款信息表\n户名："+holder+"\n开户银行：中国银行上海分行\n银行账号：1234567890123456"),"须提交退款信息表");
   assertThat(r.status()).as(holder).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
  }
 }

 @Test void fieldDescriptionsWithLegalSuffixesCannotBecomeNames() {
  for (String[] names : java.util.List.of(new String[]{"（单位名称）公司","（银行名称）银行"},
     new String[]{"XX（单位名称）有限公司","XX（银行名称）银行"},
     new String[]{"单位名称公司","银行名称银行"})) {
   var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash",
    "保证金退款信息表\n户名："+names[0]+"\n开户银行："+names[1]+"\n银行账号：1234567890123456"),"须提交退款信息表");
   assertThat(r.status()).as(java.util.Arrays.toString(names)).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
  }
 }
 @Test void actualNameStemMayIncludeAParenthesizedLocation() {
  var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash",
   "保证金退款信息表\n户名：西域智慧供应链（上海）股份公司\n开户银行：中国银行上海分行\n银行账号：1234567890123456"),"须提交退款信息表");
  assertThat(r.status()).isEqualTo(QualityCheckStatus.PASS);
 }

 @Test void maskedEntityNamesRemainUnresolved() {
  for (String[] names : java.util.List.of(new String[]{"某某公司","中国银行上海分行"},
    new String[]{"甲乙物资有限公司","某某银行"}, new String[]{"XX公司","XX银行"})) {
   var r=SubItemAndAttachmentRules.checkDepositRefund(DocumentContent.fromText("bid.pdf","hash",
    "保证金退款信息表\n户名："+names[0]+"\n开户银行："+names[1]+"\n银行账号：1234567890123456"),"须提交退款信息表");
   assertThat(r.status()).as(java.util.Arrays.toString(names)).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
  }
 }
}
