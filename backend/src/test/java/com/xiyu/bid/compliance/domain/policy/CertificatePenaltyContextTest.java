package com.xiyu.bid.compliance.domain.policy;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CertificatePenaltyContextTest {
    @Test
    void penaltyIsRetainedAsQualificationButNotTreatedAsCertificate() {
        String text = "被责令停产停业、暂扣或者吊销许可证、暂扣或者吊销执照";
        var condition = new QualificationCondition(text, null, null);
        assertThat(condition.text()).isEqualTo(text);
        assertThat(condition.certificates()).isEmpty();
        assertThat(QualificationCondition.certificateQueries(java.util.List.of(condition))).isEmpty();
    }

    @Test
    void realRequiredLicenceRemainsAQuery() {
        String text = "投标人须具有有效的食品经营许可证";
        var condition = new QualificationCondition(text, null, null);
        assertThat(QualificationCondition.certificateQueries(java.util.List.of(condition))).contains(text);
    }

    @Test
    void mixedConditionRetainsRealLicenceAndExcludesPenalty() {
        String text = "投标人须具有食品经营许可证；不得被吊销许可证";
        var condition = new QualificationCondition(text, null, null);
        assertThat(QualificationCondition.certificateQueries(java.util.List.of(condition)))
            .anyMatch(q -> q.startsWith("投标人须具有食品经营许可证"))
            .doesNotContain("不得被吊销许可证");
    }
    @Test
    void sameClauseDoesNotDiscardRequiredLicence() {
        String text = "投标人须具有食品经营许可证且不得被吊销许可证";
        var condition = new QualificationCondition(text, null, null);
        assertThat(QualificationCondition.certificateQueries(java.util.List.of(condition)))
            .anyMatch(q -> QualificationMatchPolicy.isQualificationMatch(q, "食品经营许可证"));
    }
    @Test
    void retainedLicenceWhenNoRevocationOccurred() {
        for (String text : java.util.List.of("投标人须具有食品经营许可证且未曾被吊销许可证", "投标人须具有食品经营许可证且未发生被吊销许可证")) {
            assertThat(QualificationCondition.certificateQueries(java.util.List.of(new QualificationCondition(text, null, null))))
                    .anyMatch(q -> q.startsWith("投标人须具有食品经营许可证"));
        }
    }
    @Test
    void namedLicenceRevocationIsNotCertificateRequirement() {
        var condition = new QualificationCondition("被依法吊销食品经营许可证", null, null);
        assertThat(condition.certificates()).isEmpty();
    }
    @Test
    void penaltyBeforeRequiredLicenceDoesNotEraseRequirement() {
        String text = "不得被吊销许可证且投标人须具有食品经营许可证";
        assertThat(QualificationCondition.certificateQueries(java.util.List.of(new QualificationCondition(text, null, null))))
                .contains("投标人须具有食品经营许可证");
    }
    @Test
    void licenceAlreadyRevokedIsNotARequiredCertificate() {
        assertThat(new QualificationCondition("许可证已被吊销", null, null).certificates()).isEmpty();
    }
    @Test
    void requiredValidLicenceMustSurvivePenaltyAdjective() {
        for (String text : java.util.List.of("投标人须具有未被吊销的食品经营许可证", "供应商须提供未被撤销的资质证书", "投标人须持有食品经营许可证并未被吊销许可证", "投标人须持有食品经营许可证且该证书未被吊销")) {
            assertThat(QualificationCondition.certificateQueries(java.util.List.of(new QualificationCondition(text, null, null))))
                    .as(text).isNotEmpty();
        }
    }
}
