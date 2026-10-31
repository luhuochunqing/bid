package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class R10IsoBindingTest {
    private static QualityCheckItemResult check(List<String> conditions, String text) {
        var req = TenderRequirements.empty().withMergedQualificationConditions(conditions.stream()
                .map(c -> new QualificationCondition(c, null, null)).toList());
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        return QualificationDetailReconciler.reconcile(raw, List.of(), req, DocumentContent.fromText("b", "h", text));
    }
    @Test
    void sameIsoNumberUsesCaseSpaceAndHyphenIndependentIdentity() {
        for (String name : List.of("iso9001", "ISO-9001", "ISO 9001", "ISO9001", "ISO  9001", "ISO - 9001")) {
            assertThat(check(List.of("投标人须具有ISO9001认证"), name + "质量管理体系认证\n证书编号：_____").status())
                    .as(name).isEqualTo(QualityCheckStatus.FAIL);
        }
        assertThat(check(List.of("投标人须具有ISO  9001认证"), "ISO9001认证\n证书编号：_____").status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void multipleDifferentCertificateNamesAreNotAConfirmedMandatoryDefect() {
        var req = List.of("投标人须具有ISO45001认证", "ISO9001认证（如有则提供）");
        for (String text : List.of("企业资质证明\n证书名称：ISO9001或ISO45001\n证书编号：_____",
                "企业资质证明\nISO9001或ISO45001：【此处上传证书】",
                "企业资质证明\nISO9001、ISO45001：【此处上传证书】",
                "企业资质证明\nISO9001，ISO45001：【此处上传证书】")) {
            assertThat(check(req, text).status()).as(text).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        }
    }
    @Test
    void fullIsoNumberMustMatchAndRepeatedSameIdentityIsUnambiguous() {
        var req = List.of("投标人须具有ISO9001认证");
        assertThat(check(req, "ISO90010认证\n证书编号：_____").status()).isNotEqualTo(QualityCheckStatus.FAIL);
        assertThat(check(List.of("投标人须具有ISO900100认证"), "ISO90010认证\n证书编号：_____").status())
                .isNotEqualTo(QualityCheckStatus.FAIL);
        assertThat(check(req, "企业资质证明\n证书名称：ISO9001/iso-9001\n证书编号：_____").status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void completedCertificateListInPreviousSentenceDoesNotContaminateCurrentField() {
        var req = List.of("投标人须具有ISO45001认证");
        for (String separator : List.of("；", "。")) {
            String text = "企业资质证明\nISO9001、ISO14001已提供" + separator + "ISO45001：【此处上传证书】";
            assertThat(check(req, text).status()).as(separator).isEqualTo(QualityCheckStatus.FAIL);
        }
    }
}
