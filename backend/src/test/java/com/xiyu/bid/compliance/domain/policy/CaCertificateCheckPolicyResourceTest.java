package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import com.xiyu.bid.compliance.domain.QualityEvidence;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaCertificateCheckPolicyResourceTest {

    @Test
    void check_unverifiedPlatformIdentity_requiresManualEvenWithApparentMatch() {
        for (String candidate : List.of("电子招标投标交易平台", "本次交易平台", "新成立科技公司电子招采平台")) {
            CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(candidate, "使用CA数字证书");
            QualityEvidence apparentMatch = QualityEvidence.fromKnowledgeMatch(new KnowledgeMatch(
                    KnowledgeMatch.SOURCE_CA_CERTIFICATE, "test-record", "CA证书: " + candidate + "（有效，在库）", true));
            for (List<QualityEvidence> matches : List.of(List.<QualityEvidence>of(), List.of(apparentMatch))) {
                QualityCheckItemResult result = CaCertificateCheckPolicy.check(DocumentContent.empty(), req, matches);
                assertThat(result.status()).as("unverified candidate %s", candidate)
                        .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
                assertThat(result.conclusion()).contains(candidate);
            }
        }
    }

    @Test
    void check_unverifiedIdentity_doesNotOverrideIndependentPlaceholderFailure() {
        QualityCheckItemResult result = CaCertificateCheckPolicy.check(
                DocumentContent.fromText("bid.pdf", "hash", "CA数字证书\n【此处粘贴CA证书】"),
                CaCertificateRequirement.requiredStandard("本次交易平台", "使用CA"), List.of());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void check_genericPlatformAndOnlyExpiredUnrelatedCertificate_returnsManualReview() {
        CaCertificateEvidence expired = new CaCertificateEvidence(1L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.of(2020, 1, 1), "其他集团电子招采平台", "IN_STOCK", "EXPIRED", "保管人", "持有人");
        QualityCheckItemResult result = CaCertificateCheckPolicy.check(DocumentContent.empty(),
                CaCertificateRequirement.requiredStandard("或称交易平台", "使用CA"),
                List.of(QualityEvidence.fromKnowledgeMatch(expired.toKnowledgeMatch(true))));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("电子标且指定平台，但 CA 资源库未录入该平台 CA 证书时判定 FAIL 并提示前往 CA 管理")
    void testCheck_WhenElectronicBid_AndNoMatchingCaFound_FailsWithSuggestion() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash1", "正文无证书文字");
        CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(
                "浙江政府采购网（政采云）", "本项目须在政采云平台使用 CA 签章与加密");

        // 知识库中仅有其他平台的 CA，无政采云 CA
        CaCertificateEvidence otherCa = new CaCertificateEvidence(
                101L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.now().plusMonths(6), "国家电网新源电子商务平台",
                "IN_STOCK", "ACTIVE", "张三", "李四");
        List<QualityEvidence> kbEvidences = List.of(QualityEvidence.fromKnowledgeMatch(otherCa.toKnowledgeMatch(false)));

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("未在 CA 证书管理中检索到匹配【浙江政府采购网（政采云）】");
        assertThat(result.suggestion()).contains("资源中心 - CA证书管理");
    }

    @Test
    @DisplayName("电子标且匹配到平台 CA，但证书已过期时判定 FAIL 并提示安排续期")
    void testCheck_WhenElectronicBid_AndMatchingCaExpired_FailsWithExpiredWarning() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash2", "正文内容");
        CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(
                "政采云", "本项目须在政采云平台使用 CA 签章与加密");

        CaCertificateEvidence expiredCa = new CaCertificateEvidence(
                102L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.now().minusDays(10), "政采云",
                "IN_STOCK", "EXPIRED", "王五", "赵六");
        List<QualityEvidence> kbEvidences = List.of(QualityEvidence.fromKnowledgeMatch(expiredCa.toKnowledgeMatch(true)));

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.conclusion()).contains("已过期");
        assertThat(result.suggestion()).contains("续期");
        assertThat(result.evidences()).anyMatch(e -> "ca-certificate".equals(e.knowledgeSource()));
    }

    @Test
    @DisplayName("电子标且匹配到平台有效 CA，但实体锁已被借出时判定 MANUAL_REVIEW 预警催还")
    void testCheck_WhenElectronicBid_AndMatchingCaBorrowed_ReturnsManualReview() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash3", "正文内容");
        CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(
                "政采云", "本项目须在政采云平台使用 CA 签章与加密");

        CaCertificateEvidence borrowedCa = new CaCertificateEvidence(
                103L, "ENTITY_CA", "公章", "CFCA",
                LocalDate.now().plusMonths(8), "政采云",
                "BORROWED", "ACTIVE", "张主管", "借用人孙七");
        List<QualityEvidence> kbEvidences = List.of(QualityEvidence.fromKnowledgeMatch(borrowedCa.toKnowledgeMatch(true)));

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).contains("已借出");
        assertThat(result.suggestion()).contains("催还");
        assertThat(result.evidences()).anyMatch(e -> "ca-certificate".equals(e.knowledgeSource()));
    }

    @Test
    @DisplayName("电子标无需附图且匹配到平台有效在库 CA 时直接判定 PASS 并沉淀证据")
    void testCheck_WhenElectronicBid_AndMatchingCaValidAndInStock_PassesWithCaEvidence() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash4", "正文内容，无需贴证书截图");
        // requireCertificateCopy 为 false（无需正文贴图）
        CaCertificateRequirement req = new CaCertificateRequirement(
                true, "政采云", false, true, "本项目须在政采云平台使用 CA 签章与加密");

        CaCertificateEvidence validCa = new CaCertificateEvidence(
                104L, "ENTITY_CA", "公章+法人章", "CFCA",
                LocalDate.now().plusMonths(12), "政采云",
                "IN_STOCK", "ACTIVE", "张主管", "周八");
        List<QualityEvidence> kbEvidences = List.of(QualityEvidence.fromKnowledgeMatch(validCa.toKnowledgeMatch(true)));

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("有效 CA 证书");
        assertThat(result.evidences()).anyMatch(e -> "ca-certificate".equals(e.knowledgeSource()));
    }

    @Test
    @DisplayName("电子标正文附有效证明且匹配在库 CA 时判定 PASS")
    void testCheck_WhenCopyRequiredAndDocContainsProof_PassesWithBothEvidences() {
        String certText = """
                附件：CA数字证书认证有效性证明
                颁发机构：中国金融认证中心 (CFCA)
                证书序列号：7A8B9C0D1E2F3344
                有效期至：2027年12月31日
                """;
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash4_with_cert", certText);
        CaCertificateRequirement req = CaCertificateRequirement.requiredStandard(
                "政采云", "本项目须在政采云平台使用 CA 签章并在标书中附证明");

        CaCertificateEvidence validCa = new CaCertificateEvidence(
                105L, "ENTITY_CA", "公章+法人章", "CFCA",
                LocalDate.now().plusMonths(12), "政采云",
                "IN_STOCK", "ACTIVE", "张主管", "周八");
        List<QualityEvidence> kbEvidences = List.of(QualityEvidence.fromKnowledgeMatch(validCa.toKnowledgeMatch(true)));

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, kbEvidences);

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("已在【CA证书管理】检索到匹配【政采云】的有效 CA 证书");
        assertThat(result.evidences()).anyMatch(e -> "ca-certificate".equals(e.knowledgeSource()));
    }

    @Test
    @DisplayName("招标文件明确免收或为纸质标时直通 PASS，不受 CA 资源库有无证书影响")
    void testCheck_WhenNotRequired_PassesRegardlessOfCaResource() {
        DocumentContent doc = DocumentContent.fromText("bid.pdf", "hash5", "纸质标正文");
        CaCertificateRequirement req = CaCertificateRequirement.notRequired("纸质标免收CA");

        QualityCheckItemResult result = CaCertificateCheckPolicy.check(doc, req, Collections.emptyList());

        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(result.conclusion()).contains("未要求 CA 电子签章或数字证书");
    }
}
