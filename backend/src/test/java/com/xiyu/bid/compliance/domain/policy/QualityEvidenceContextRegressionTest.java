package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class QualityEvidenceContextRegressionTest {
    @Test
    void minimumRevenueRequirementIsNotANegativeDeviation() {
        String text = "投标人应具有良好的商业信誉和健全的财务会计制度，2023年度主营业务收入不得低于4亿元人民币。";
        var doc = DocumentContent.fromText("bid.docx", "hash", text);
        var result = ResponsivenessReviewPolicy.check(doc);
        assertThat(result == null || result.status() != QualityCheckStatus.FAIL).isTrue();
    }

    @Test
    void placeholderEvidenceUsesThePageThatTriggeredTheFinding() {
        var pages = List.of(
                new DocumentContent.PageContent(42, "延保服务协议：乙方：_____年", List.of()),
                new DocumentContent.PageContent(80, "资质证书证明材料：请填写编号_____", List.of()));
        var doc = new DocumentContent("bid.docx", "hash", "", pages);
        var result = QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty()).orElseThrow();
        assertThat(result.evidences().get(0).pageNumber()).isEqualTo(80);
        assertThat(result.evidences().get(0).quote()).contains("资质证书证明材料");
    }
    @Test
    void actualRevenueBelowThresholdRemainsNegativeDeviation() {
        var doc = DocumentContent.fromText("bid.docx", "hash", "我方实际主营收入低于招标要求，存在负偏离。");
        var result = ResponsivenessReviewPolicy.check(doc);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void unrelatedWarrantyPlaceholderOnSamePageMustNotFailQualification() {
        String text = "资质证书：ISO9001证书扫描件已提供。\n延保服务协议书\n甲方：西域\n乙方：_____";
        var doc = DocumentContent.fromText("bid.docx", "hash", text);
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isEmpty();
    }
    @Test
    void unrelatedPlaceholderAfterCommaMustNotInheritQualificationReference() {
        var doc = DocumentContent.fromText("bid.docx", "hash", "资质证书已提交，延保协议乙方：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isEmpty();
    }
    @Test
    void previousNarrativeLineMustNotActAsSectionHeading() {
        var doc = DocumentContent.fromText("bid.docx", "hash", "资质证书扫描件已经提供\n甲方：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isEmpty();
    }
    @Test
    void minimumRevenueNotYingIsNotNegative() {
        var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("bid", "hash", "投标人2023年度收入不应低于4亿元。"));
        assertThat(result == null || result.status() != QualityCheckStatus.FAIL).isTrue();
    }
    @Test
    void laterRelevantPlaceholderOnSameLineIsFound() {
        var doc = DocumentContent.fromText("bid", "hash", "延保编号_____，资质证书编号_____");
        var result = QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences().get(0).quote()).contains("资质证书编号_____");
    }
    @Test
    void certificateHeadingAndFollowingFieldsAreFound() {
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n证书名称：_____\n证书编号：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isPresent();
    }
    @Test
    void numberedHeadingWithBlankLineAndCertificateNumberIsRecognized() {
        var doc = DocumentContent.fromText("bid", "hash", "第四部分 企业资质证明\n\n证书编号：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isPresent();
    }
    @Test
    void unrelatedFieldBelowHeadingIsNotACertificatePlaceholder() {
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n甲方：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isEmpty();
    }
    @Test
    void minimumRevenueWithOcrSpaceIsNotNegative() {
        for (String text : List.of("主营业务收入不得 低于4亿元", "主营业务收入不得\n低于4亿元", "投标人收入不可低于4亿元")) {
            var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("bid", "hash", text));
            assertThat(result == null || result.status() != QualityCheckStatus.FAIL).as(text).isTrue();
        }
    }
    @Test
    void filledCertificateNameDoesNotEraseSectionContext() {
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n证书名称：ISO9001\n证书编号：_____");
        assertThat(QualificationMatchPolicy.checkFirstStage(doc, TenderRequirements.empty())).isPresent();
    }
}
