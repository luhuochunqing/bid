package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class R8EvidenceBindingTest {
    private static TenderRequirements requirements(String... values) {
        return TenderRequirements.empty().withMergedQualificationConditions(
                java.util.Arrays.stream(values).map(v -> new QualificationCondition(v, null, null)).toList());
    }
    private static QualityCheckItemResult qualification(TenderRequirements req, String text) {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        return QualificationDetailReconciler.reconcile(raw, List.of(), req, DocumentContent.fromText("b", "h", text));
    }
    private static QualityCheckItemResult responsiveness(String text) {
        var bid = DocumentContent.fromText("bid", "hash", text);
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(new LlmVerdict("PASS", "", "全部满足", "")));
        return BidDocumentQualityEngine.evaluateAll(bid, DocumentContent.empty(), List.of(), TenderRequirements.empty(),
                Set.of(), llm, (p, s) -> { }).stream()
                .filter(x -> x.itemCode().equals(BidQualityCheckItem.RESPONSIVENESS_REVIEW.getCode()))
                .findFirst().orElseThrow();
    }
    @Test
    void mandatoryCertificateFieldsBindDespiteSuffixOrNextLine() {
        var req = requirements("投标人须具有食品经营许可证", "ISO45001认证（如有则提供）");
        for (String text : List.of("食品经营许可证复印件：【此处粘贴食品经营许可证扫描件】",
                "食品经营许可证证书编号：_____", "食品经营许可证\n证书编号：_____")) {
            assertThat(qualification(req, text).status()).as(text).isEqualTo(QualityCheckStatus.FAIL);
        }
    }
    @Test
    void laterMandatoryPlaceholderOnSamePageWins() {
        var req = requirements("投标人须具有安全生产许可证", "食品经营许可证（如有则提供）");
        String text = "食品经营许可证：【此处上传许可证】\n安全生产许可证复印件：【此处上传许可证】";
        var result = qualification(req, text);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences().get(0).quote()).contains("安全生产许可证复印件");
    }
    @Test
    void unrelatedPlaceholderDoesNotInheritOtherCertificateRequirement() {
        var req = requirements("投标人须具有ISO9001认证");
        assertThat(qualification(req, "食品经营许可证：【此处粘贴食品经营许可证扫描件】").status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void explicitOwnFailureCannotBeUpgradedByModel() {
        for (String text : List.of("我方不能满足招标文件要求", "我方无法满足招标文件要求",
                "本企业收入不足4亿元，未能满足资格要求", "我方实际营业收入不足4亿元", "我方资质不符合招标要求",
                "我方未能满足招标要求", "我方未能符合招标要求", "我方不能按招标文件要求履约",
                "我方不能满足招标要求且承诺不得转包", "我方实际资质不符合招标要求并承诺不得弄虚作假",
                "我方未能达到招标要求且承诺不允许分包")) {
            assertThat(responsiveness(text).status()).as(text).isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void changedCertificateNameIsUsedForNextPlaceholder() {
        var req = requirements("投标人须具有安全生产许可证", "食品经营许可证（如有则提供）");
        for (String text : List.of("食品经营许可证\n证书编号：_____\n证书名称：安全生产许可证\n证书编号：_____",
                "请上传安全生产许可证：【此处上传许可证】", "安全生产许可证（有效）\n证书编号：_____")) {
            assertThat(qualification(req, text).status()).as(text).isEqualTo(QualityCheckStatus.FAIL);
        }
        String reverse = "安全生产许可证\n证书编号：沪123456\n证书名称：食品经营许可证\n证书编号：_____";
        assertThat(qualification(req, reverse).status()).isNotEqualTo(QualityCheckStatus.FAIL);
        String optionalHeading = "安全生产许可证\n证书编号：沪123456\n食品经营许可证（如有则提供）\n证书编号：_____";
        assertThat(qualification(req, optionalHeading).status()).isNotEqualTo(QualityCheckStatus.FAIL);
        String sameLine = "食品经营许可证（已提供）、安全生产许可证：【此处粘贴安全生产许可证扫描件】";
        assertThat(qualification(req, sameLine).status()).isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void nonBidderAndUnknownSubjectsCannotProveMissingCompanyCertificate() {
        for (String requirement : List.of("投标人须提供项目经理安全生产许可证",
                "供应商须提供所投产品安全生产许可证", "须提供安全生产许可证")) {
            assertThat(qualification(requirements(requirement), "企业资质证书\n安全生产许可证：【此处粘贴许可证扫描件】")
                    .status()).as(requirement).isNotEqualTo(QualityCheckStatus.FAIL);
        }
    }
    @Test
    void missingTenderRequirementsCannotProveMandatoryPlaceholder() {
        assertThat(qualification(TenderRequirements.missingSentinel(), "安全生产许可证：【此处上传许可证】").status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void citedRulesAndPositiveSelfStatementDoNotBecomeFailures() {
        for (String text : List.of("我方知悉评审规则：投标人无法满足资格要求者不得通过。",
                "我方完全满足招标文件要求。")) {
            assertThat(responsiveness(text).status()).as(text).isNotEqualTo(QualityCheckStatus.FAIL);
        }
    }
    @Test
    void ruleCitationAloneCannotBeUpgradedByModel() {
        for (String text : List.of("我方提供如下评审引用：投标人收入低于4亿元不得分。",
                "我方知悉评审规则：投标人收入低于4亿元不得分。")) {
            assertThat(responsiveness(text).status()).as(text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
}
