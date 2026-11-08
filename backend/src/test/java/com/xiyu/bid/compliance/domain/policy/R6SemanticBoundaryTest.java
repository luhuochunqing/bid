package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class R6SemanticBoundaryTest {
    @Test
    void obligationWithLeadMustNotVanishOrAutoPass() {
        String text = "根据法律规定投标人须持有食品经营许可证且不得被吊销许可证";
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(text, null, null)));
        assertThat(req.certificateRequirements()).isNotEmpty();
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "已满足", List.of());
        var bid = DocumentContent.fromText("bid", "hash", "未提供食品经营许可证");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req, bid).status()).isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void penaltyDeclarationIsNotARequiredLicence() {
        for (String s : List.of("投标人应提供未曾被吊销食品经营许可证的承诺书", "投标人须提供未受到吊销许可证行政处罚的声明")) {
            var req = new QualificationCondition(s, null, null);
            assertThat(req.text()).isEqualTo(s);
            assertThat(QualificationCondition.certificateQueries(List.of(req))).as(s).isEmpty();
        }
    }
    @Test
    void unclearBlankCannotBecomePass() {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "全部满足", List.of());
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n有效期至：_____");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), TenderRequirements.empty(), doc).status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void unicodeSpacesDoNotTurnMinimumRuleIntoNegative() {
        for (String s : List.of("投标人收入不得\u00a0低于4亿元", "投标人收入不得\u3000低于4亿元")) {
            var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("bid", "hash", s));
            assertThat(result == null || result.status() != QualityCheckStatus.FAIL).as(s).isTrue();
        }
    }
    @Test
    void compliantDeviationTableWithMinimumRuleIsNotNegative() {
        String t = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n| 1 | 2.1 | 主营收入不得低于4亿元 | 响应 | 无偏离 | 符合要求，收入不得低于4亿元 |";
        var doc = DocumentContent.fromText("bid", "hash", t);
        assertThat(DeviationTableExtractor.extract(doc).negativeRows()).isEmpty();
        var result = ResponsivenessReviewPolicy.check(doc);
        assertThat(result == null || result.status() != QualityCheckStatus.FAIL).isTrue();
    }
    @Test
    void explicitNegativeDeviationStillFails() {
        var doc = DocumentContent.fromText("bid", "hash", "商务偏离表：\n我方实际收入低于要求，存在负偏离。");
        assertThat(ResponsivenessReviewPolicy.check(doc).status()).isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void genericConditionsDoNotBecomeLicenceRequirement() {
        var req = new QualificationCondition("供应商须具备下列条件：未被吊销许可证", null, null);
        assertThat(QualificationCondition.certificateQueries(List.of(req))).isEmpty();
    }
    @Test
    void ambiguousBlankMustStayManualAndNotMaskLaterProvenBlank() {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "全部满足", List.of());
        var unknown = DocumentContent.fromText("b", "h", "企业资质证明\n证书信息：_____");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), TenderRequirements.empty(), unknown).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var pages = List.of(new DocumentContent.PageContent(1, "企业资质证明\n证书信息：_____", List.of()),
                new DocumentContent.PageContent(2, "资质证书编号：_____", List.of()));
        var both = new DocumentContent("bid", "hash", "企业资质证明 证书信息 资质证书编号", pages);
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), TenderRequirements.empty(), both).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
    @Test
    void noDeviationLabelCannotHideActualNegativeInTable() {
        String t = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n| 1 | 2.1 | 主营收入不少于4亿元 | 响应 | 无偏离 | 我方实际收入低于要求 |";
        var doc = DocumentContent.fromText("bid", "hash", t);
        assertThat(ResponsivenessReviewPolicy.check(doc).status()).isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void unresolvedConditionAndAmbiguousBlankNeverAllowEngineBoundaryPass() {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "全部满足", List.of());
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(
                new QualificationCondition("投标人或供应商须具有ISO9001认证", null, null)));
        assertThat(req.hasUnresolvedCertificateRequirements()).isTrue();
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n证书信息：_____");
        assertThat(SemanticEvidenceBoundary.reconcileQualificationApplicability(raw, doc, req).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }
    @Test
    void ambiguousBlankDoesNotMaskKnownExpiredCertificate() {
        String clause = "投标人须具有ISO9001认证";
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(clause, null, null)));
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        var expired = new com.xiyu.bid.compliance.domain.QualificationDetail(clause, "ISO9001认证", "EXPIRED", 1, "证书正文", -1, "已过期");
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n证书信息：_____");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(expired), req, doc).status())
                .isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void declarationMustNotHideAnIndependentRequiredCertificate() {
        String text = "投标人应提供食品经营许可证及未被吊销许可证的承诺书";
        var condition = new QualificationCondition(text, null, null);
        var requirements = TenderRequirements.empty().withMergedQualificationConditions(List.of(condition));
        assertThat(requirements.certificateRequirements()).isNotEmpty();
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "已满足", List.of());
        var bid = DocumentContent.fromText("bid", "hash", "未提供食品经营许可证");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), requirements, bid).status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void certificateHeadingAndEmptyFieldAcrossPagesCannotPass() {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "已满足", List.of());
        var pages = List.of(new DocumentContent.PageContent(1, "企业资质证明", List.of()),
                new DocumentContent.PageContent(2, "证书有效期至：_____", List.of()));
        var doc = new DocumentContent("bid", "hash", "企业资质证明 证书有效期至", pages);
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), TenderRequirements.empty(), doc).status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void ordinaryBuDiyuIsNotNegative() {
        for (String text : List.of("投标人收入不低于4亿元", "我方营业收入不低于4亿元")) {
            var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("b", "h", text));
            assertThat(result == null || result.status() != QualityCheckStatus.FAIL).as(text).isTrue();
        }
    }
    @Test
    void interveningCertificateFieldDoesNotHideBlank() {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        var doc = DocumentContent.fromText("bid", "hash", "企业资质证明\n证书名称：ISO9001\n发证机构：某认证公司\n证书编号：_____");
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), TenderRequirements.empty(), doc).status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void bidderPenaltyIsNotACertificate() {
        for (String text : List.of("投标人未被吊销食品经营许可证", "投标人不存在被吊销许可证的情形", "供应商须具备下列条件：未被吊销许可证的企业", "投标人须提供具有下列条件的承诺书：未被吊销许可证"))
            assertThat(QualificationCondition.certificateQueries(List.of(new QualificationCondition(text, null, null)))).as(text).isEmpty();
    }
    @Test
    void simultaneousDeclarationDoesNotEraseCertificate() {
        String text = "投标人应提供食品经营许可证同时提供未被吊销许可证的承诺书";
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(text, null, null)));
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        assertThat(req.certificateRequirements()).isNotEmpty();
        assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), req, DocumentContent.fromText("bid", "hash", "未提供许可证")).status())
                .isNotEqualTo(QualityCheckStatus.PASS);
    }
    @Test
    void unrelatedContractBlankInQualificationSentenceMustNotFail() {
        for (String s : List.of("资质证书已全部提交且延保协议合同编号：_____", "资质证书已提供（延保合同编号_____）")) {
            var result = QualificationMatchPolicy.checkFirstStage(DocumentContent.fromText("bid", "hash", s), TenderRequirements.empty());
            assertThat(result.isEmpty() || result.get().status() != QualityCheckStatus.FAIL).as(s).isTrue();
        }
    }
    @Test
    void unrelatedNextSectionCannotBecomeCertificateDefect() {
        for (String text : List.of("企业资质证明\n报价一览表\n名称：_____", "企业资质证明\n项目联系人信息\n编号：_____")) {
            var result = QualificationMatchPolicy.checkFirstStage(DocumentContent.fromText("b", "h", text), TenderRequirements.empty());
            assertThat(result.isEmpty() || result.get().status() != QualityCheckStatus.FAIL).as(text).isTrue();
        }
    }
    @Test
    void concreteCertificateTitleWithBlankCannotAutoPass() {
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        for (String text : List.of("安全生产许可证\n证书编号：_____", "食品经营许可证\n有效期至：_____", "ISO9001质量管理体系认证证书\n证书编号：_____")) {
            var doc = DocumentContent.fromText("b", "h", text);
            assertThat(QualificationDetailReconciler.reconcile(raw, List.of(), TenderRequirements.empty(), doc).status())
                    .as(text).isNotEqualTo(QualityCheckStatus.PASS);
        }
    }
    @Test
    void ambiguousPlaceholderPreservesCalibratedDetail() {
        String clause = "投标人或供应商须具有ISO9001认证";
        var req = TenderRequirements.empty().withMergedQualificationConditions(List.of(new QualificationCondition(clause, null, null)));
        var raw = QualityCheckItemResult.pass(BidQualityCheckItem.QUALIFICATION_MATCH, "满足", List.of());
        var detail = new com.xiyu.bid.compliance.domain.QualificationDetail(clause, "ISO9001认证", "MATCHED", 1, "ISO9001扫描件", 300, "已匹配");
        var doc = DocumentContent.fromText("b", "h", "企业资质证明\n证书信息：_____");
        var result = QualificationDetailReconciler.reconcile(raw, List.of(detail), req, doc);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.details().get(0).status()).isEqualTo("ATTENTION");
        assertThat(result.details().get(0).remark()).contains("条件适用范围或作用对象待确认").doesNotContain("[UNKNOWN]");
    }
    @Test
    void conflictingActualShortfallCannotPass() {
        String table = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n| 1 | 2.1 | 收入不得低于4亿 | 响应 | 无偏离 | 我方实际未能达到不得低于4亿元的要求 |";
        var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("b", "h", table));
        assertThat(result == null || result.status() != QualityCheckStatus.PASS).isTrue();
    }
    @Test
    void citedLowRevenueRulesAreNotActualDeviation() {
        for (String text : List.of("投标人营业收入如低于4亿元不得分", "投标人营业收入低于4亿元视为不合格", "投标人营业收入不得\u200b低于4亿元")) {
            var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("b", "h", text));
            assertThat(result == null || result.status() != QualityCheckStatus.FAIL).as(text).isTrue();
        }
        var actual = ResponsivenessReviewPolicy.check(DocumentContent.fromText("b", "h", "我方实际收入3亿元，低于招标要求4亿元"));
        assertThat(actual.status()).isEqualTo(QualityCheckStatus.FAIL);
    }
    @Test
    void bidderNonRevocationStatementsAreNotCertificateRequirements() {
        for (String text : List.of("投标人从未被吊销许可证", "投标人近三年未被吊销许可证", "投标人没有被吊销许可证", "供应商不存在吊销许可证的情况", "供应商须具备下列条件：未被吊销许可证的企业"))
            assertThat(QualificationCondition.certificateQueries(List.of(new QualificationCondition(text, null, null)))).as(text).isEmpty();
    }
    @Test
    void tableOwnShortfallWithoutActualWordCannotPass() {
        String table = "商务偏离表\n| 序号 | 条款号 | 招标参数要求 | 投标响应参数 | 偏离情况 | 说明 |\n| 1 | 2.1 | 收入不得低于4亿 | 响应 | 无偏离 | 我方未能达到不得低于4亿元的要求 |";
        var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("b", "h", table));
        assertThat(result == null || result.status() != QualityCheckStatus.PASS).isTrue();
    }
    @Test
    void bidderAcknowledgingRuleIsNotOwnShortfall() {
        var result = ResponsivenessReviewPolicy.check(DocumentContent.fromText("b", "h", "我方知悉投标人营业收入低于4亿元视为不合格。"));
        assertThat(result == null || result.status() != QualityCheckStatus.FAIL).isTrue();
    }
    @Test
    void certificateMentionedInsidePenaltySituationDocumentIsNotMandatoryCertificate() {
        for (String text : List.of("投标人应提供持有被吊销许可证的情况说明", "投标人应提交有关吊销许可证情况的说明")) {
            var condition = new QualificationCondition(text, null, null);
            assertThat(condition.certificates()).as(text)
                    .noneMatch(c -> c.applicability() == QualificationCondition.Applicability.MANDATORY);
        }
    }
    @Test
    void requiredCertificateAlongsideDeclarationRemains() {
        var condition = new QualificationCondition("投标人须持有食品经营许可证并提供未被吊销的声明", null, null);
        assertThat(QualificationCondition.certificateQueries(List.of(condition))).isNotEmpty();
    }
}
