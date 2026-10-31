package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class R9CertificateBindingTest {
    private static TenderRequirements requirements() {
        return TenderRequirements.empty().withMergedQualificationConditions(List.of(
                new QualificationCondition("投标人须具有安全生产许可证", null, null),
                new QualificationCondition("食品经营许可证（如有则提供）", null, null)));
    }
    private static QualityCheckItemResult check(String text) {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        return QualificationDetailReconciler.reconcile(raw, List.of(), requirements(),
                DocumentContent.fromText("bid", "hash", text));
    }
    @Test
    void optionalHeadingChangesIdentityForArbitraryAnnotation() {
        for (String annotation : List.of("如有", "可选", "若有", "如有请提供", "其他注释", "如有应提供", "持有时提供")) {
            String text = "安全生产许可证\n证书编号：沪123456\n食品经营许可证（" + annotation + "）\n证书编号：_____";
            assertThat(check(text).status()).as(annotation).isNotEqualTo(QualityCheckStatus.FAIL);
        }
        assertThat(check("安全生产许可证\n证书编号：沪123456\n许可证名称：食品经营许可证\n证书编号：_____").status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void placeholderNamesMandatoryCertificateAfterDifferentCompletedStatement() {
        String text = "企业资质证明\n食品经营许可证已提供，【此处上传安全生产许可证】";
        assertThat(check(text).status()).isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void sameFieldConflictingNamesRemainManual() {
        for (String text : List.of("企业资质证明\n食品经营许可证：【此处上传安全生产许可证】",
                "食品经营许可证（如有则提供）\n【此处上传安全生产许可证】",
                "证书名称：食品经营许可证\n【此处上传安全生产许可证】")) {
            assertThat(check(text).status()).as(text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test
    void sectionChangeDoesNotCarryPriorCertificateIntoUnrelatedMaterial() {
        String text = "安全生产许可证\n证书编号：沪123456\n售后服务承诺书\n联系人：_____";
        assertThat(check(text).status()).isNotEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void isoCertificateTitleBindsItsFollowingField() {
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(
                new QualificationCondition("投标人须具有ISO9001认证", null, null)));
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        for (String title : List.of("ISO9001质量管理体系认证", "ISO9001质量管理体系认证（有效）")) {
            var doc = DocumentContent.fromText("bid", "hash", title + "\n证书编号：_____");
            assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req, doc).status())
                    .as(title).isEqualTo(QualityCheckStatus.FAIL);
        }
    }
}
