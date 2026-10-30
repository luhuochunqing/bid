// Input: 项目1117报告56的脱敏正文/抽样/图片附件边界
// Output: 断言正文定位、抽样不代表缺页、可证实失败不被放宽
// Pos: compliance/domain/policy/ - 证据边界回归（spec079 AC06/07/08）
// 维护声明: 与语义页面检索和LLM裁决证据契约同步。
package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.QualityCheckItemResult;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityEvidenceBoundaryTest {
    @Test
    void sampledContextExplainsThatPageGapsDoNotProveMissingPdfPages() {
        DocumentContent doc = document(page(8, "授权委托书 委托人 代理权限"),
                page(11, "扫描图片页".repeat(200)), page(12, "法定代表人身份证明 姓名"));
        String result = SemanticPageRetriever.retrieveContext(
                BidQualityCheckItem.AUTHORIZATION_LETTER, TenderRequirements.empty(), doc, 80);
        assertThat(result).contains("相关页抽样", "不代表原文件缺页");
    }

    @Test
    void unrelatedScoringRequirementsDoNotDisplaceAuthorizationBody() {
        var req = new TenderRequirements(List.of(
                new TenderRequirements.ScoringItem("云原生架构", 10.0, "双活容灾")),
                List.of("超算认证"), List.of(), List.of(), List.of(), false);
        var doc = document(page(11, "授权委托书 授权范围"),
                page(50, "云原生架构 双活容灾 超算认证".repeat(12)));
        String result = SemanticPageRetriever.retrieveContext(
                BidQualityCheckItem.AUTHORIZATION_LETTER, req, doc, 80);
        assertThat(result).contains("--- [第 11 页] ---");
        assertThat(result).doesNotContain("--- [第 50 页] ---");
    }

    @Test
    void unpaginatedFallbackAlsoDeclaresThatTruncatedTextCannotProveAbsence() {
        var doc = new DocumentContent("bid.pdf", "fallback", "授权委托书正文".repeat(100), List.of());
        String result = SemanticPageRetriever.retrieveContext(
                BidQualityCheckItem.AUTHORIZATION_LETTER, TenderRequirements.empty(), doc, 80);
        assertThat(result).contains("相关页抽样", "不代表原文件缺页");
    }

    @Test
    void authorizationEvidenceUsesBodyRatherThanCatalog() {
        DocumentContent doc = document(page(2, "目录\n三、授权委托书................11"),
                page(11, "授权委托书\n委托人：甲公司 授权期限：______ 代理权限：办理投标"));
        var evidences = EvidenceAssembler.semanticEvidences(BidQualityCheckItem.AUTHORIZATION_LETTER,
                doc, DocumentContent.empty(), TenderRequirements.empty(), List.of());
        assertThat(evidences).anySatisfy(e -> {
            assertThat(e.pageNumber()).isEqualTo(11);
            assertThat(e.quote()).contains("授权期限");
        });
    }

    @Test
    void idEvidenceLocatesActualProofPage() {
        DocumentContent doc = document(page(2, "目录\n身份证明................10"),
                page(10, "法定代表人身份证明 身份证扫描件粘贴处"));
        var evidences = EvidenceAssembler.semanticEvidences(BidQualityCheckItem.ID_AUTH_LETTER,
                doc, DocumentContent.empty(), TenderRequirements.empty(), List.of());
        assertThat(evidences).anySatisfy(e -> assertThat(e.pageNumber()).isEqualTo(10));
    }

    @Test
    void unlocatedAuthorizationAbsenceVerdictRequiresManualReview() {
        var result = failVerdict(BidQualityCheckItem.AUTHORIZATION_LETTER,
                document(page(1, "投标文件封面")), "投标文件未提供授权委托书正文");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void textPlaceholderCannotProveThatIdImagesAreAbsent() {
        var result = failVerdict(BidQualityCheckItem.ID_AUTH_LETTER,
                document(page(10, "法定代表人身份证明 身份证扫描件粘贴处")),
                "仅保留身份证扫描件粘贴处占位模板，完全未附实际图片证件");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void sampledPageGapCannotBecomeDefinitiveMissingChapter() {
        DocumentContent doc = document(page(8, "授权委托书 委托人 代理权限"),
                page(11, "扫描页内容".repeat(16000)), page(12, "法定代表人身份证明"));
        var result = failVerdict(BidQualityCheckItem.AUTHORIZATION_LETTER, doc,
                "第8页后直接跳至第12页，缺失第11页授权委托书正文");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void explicitlyLocatedUnfilledAuthorizationFieldStillFails() {
        var result = failVerdict(BidQualityCheckItem.AUTHORIZATION_LETTER,
                document(page(11, "授权委托书 委托人：甲公司 受托人：乙 授权期限：______")),
                "授权委托书的授权期限字段保留未填写下划线");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).anySatisfy(e -> assertThat(e.pageNumber()).isEqualTo(11));
    }

    @Test
    void frontTableEvidenceUsesCommercialResponseInsteadOfQuotedVetoRule() {
        DocumentContent doc = document(page(4, "投标人须知前附表规定：负偏离将否决其投标。"),
                page(40, "商务条款响应表\n交货期：30天 质保期：12个月 付款方式：月结\n我司完全响应"));
        var evidences = EvidenceAssembler.semanticEvidences(BidQualityCheckItem.FRONT_TABLE_RESPONSE,
                doc, DocumentContent.empty(), TenderRequirements.empty(), List.of());
        assertThat(evidences).anySatisfy(e -> {
            assertThat(e.pageNumber()).isEqualTo(40);
            assertThat(e.quote()).contains("交货期");
        });
    }

    @Test
    void commercialResponseIsAcceptedWithoutLiteralFrontTableHeading() {
        DocumentContent doc = document(page(40,
                "商务条款响应表\n交货期：30天；质保期：12个月；付款方式：月结；我司完全响应。"));
        var result = FrontTableResponsePolicy.check(doc, null);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void commercialResponseWithExplicitBlankStillFails() {
        DocumentContent doc = document(page(40,
                "商务条款响应表\n交货期：______；质保期：12个月；我司完全响应。"));
        var result = FrontTableResponsePolicy.check(doc, null);
        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void sampledAbsenceOfFinancialStatementsRequiresManualReview() {
        var doc = document(page(10, "财务状况 审计报告"), page(100, "附录".repeat(40000)));
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW, doc,
                "投标文件未提供完整的财务报表正文");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void financialAbsenceIsNotProvenByAnUnrelatedAuthorizationBlankOnSamePage() {
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(10, "财务报表封面，后附扫描件\n授权期限：______")),
                "投标文件未提供财务报表");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void financialAbsenceIsNotProvenByAdmissionAboutMissingManufacturerAuthorization() {
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(10, "财务报表封面，后附扫描件\n我司未提供厂家授权书。")),
                "投标文件未提供财务报表");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void exampleSelfAdmissionMustNotBecomeBidderAdmissionOfMissingStatements() {
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(10, "财务报表说明模板\n请填写“我司未提供财务报表”的原因。")),
                "投标文件未提供财务报表");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void financialCoverTitleCannotProveThatFollowingScannedStatementsAreAbsent() {
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(10, "财务报表封面，后附扫描件")), "投标文件未提供财务报表");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void deterministicCatalogOnlyFailureCannotBypassEngineFinalEvidenceBoundary() {
        var doc = document(page(2, "目录\n前附表响应表................40"), page(40, "扫描图像页"));
        var results = BidDocumentQualityEngine.evaluateAll(doc, DocumentContent.empty(), List.of(),
                TenderRequirements.empty(), java.util.Set.of(), null, (progress, step) -> { });
        var result = results.stream().filter(r -> r.itemCode().equals(BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void locatedBidderAdmissionOfFinancialNonSubmissionStillFails() {
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(10, "财务材料说明：我司未提供财务报表。")), "投标人明确未提供财务报表");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void concreteUnfilledResponseFieldStillFailsAtEngineBoundary() {
        var doc = document(page(40, "商务条款响应表\n交货期：______；质保期：12个月；我司响应。"));
        var results = BidDocumentQualityEngine.evaluateAll(doc, DocumentContent.empty(), List.of(),
                TenderRequirements.empty(), java.util.Set.of(), null, (progress, step) -> { });
        var result = results.stream().filter(r -> r.itemCode().equals(BidQualityCheckItem.FRONT_TABLE_RESPONSE.getCode()))
                .findFirst().orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void explicitlyUnknownFinancialContractIsManualRatherThanExemptOrMissing() {
        var unknown = new FinancialRequirement(null, null, List.of(), List.of(), null,
                "仅定位到可选模板，未确认强制财务要求");
        var result = FinancialStatementCheckPolicy.check(DocumentContent.empty(), unknown);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unknownFinancialRequirementCannotTurnOptionalPlaceholderIntoHardFailure() {
        var unknown = new FinancialRequirement(null, null, List.of(), List.of(), null,
                "仅定位到可选模板，未确认强制财务要求");
        var result = FinancialStatementCheckPolicy.check(
                document(page(10, "【此处粘贴财务审计报告】")), unknown);
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(FinancialStatementCheckPolicy.isHardAntiFakeFail(result)).isFalse();
    }

    @Test
    void unrelatedCreditExcerptCannotSupportDefinitiveFinancialAbsence() {
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(10, "信用中国纳税记录")), "投标文件未提供财务报表");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void locatedFinancialContradictionStillFails() {
        var doc = document(page(10, "财务审计报告 资产总计100 负债合计200 所有者权益50"));
        var result = failVerdict(BidQualityCheckItem.QUALIFICATION_REVIEW, doc,
                "财务报表资产总计100与负债200及权益50不平衡");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void explicitlyOptionalFinancialRequirementDoesNotBecomeMandatoryInPrompt() {
        var req = TenderRequirements.empty().withFinancialRequirement(FinancialRequirement.notRequired(""));
        String prompt = LlmAdjudicator.buildPrompt(BidQualityCheckItem.QUALIFICATION_REVIEW,
                document(page(1, "财务情况说明")), DocumentContent.empty(), req, List.of());
        assertThat(prompt).contains("财务状况要求: 招标文件明确不要求财务证明");
    }

    @Test
    void exemptionWithMandatoryBalanceSheetCannotPassEmptyBid() {
        var req = DeterministicFinancialExtractor.extract(
                "本项目无需提供财务审计报告但须提供2025年资产负债表。");
        assertThat(FinancialStatementCheckPolicy.check(DocumentContent.empty(), req).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void financialExemptionRequiresHumanConfirmation() {
        assertThat(FinancialStatementCheckPolicy.check(DocumentContent.empty(),
                FinancialRequirement.notRequired("无需提供财务证明")).status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void aiCannotAutomaticallyPassCompositeQualificationReview() {
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(
                new LlmVerdict("PASS", "", "财务已豁免，资格全部满足", "")));
        var result = llm.adjudicateItem(BidQualityCheckItem.QUALIFICATION_REVIEW,
                DocumentContent.empty(), DocumentContent.empty(), TenderRequirements.empty(), List.of(), List.of());
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).doesNotContain("全部满足", "已豁免");
    }

    @Test
    void finalEngineCannotPublishQualificationPassFromAi() {
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(
                new LlmVerdict("PASS", "", "资格材料齐全", "")));
        var results = BidDocumentQualityEngine.evaluateAll(document(page(1, "投标文件")),
                DocumentContent.empty(), List.of(), TenderRequirements.empty(), java.util.Set.of(), llm, (p, s) -> { });
        assertThat(results).filteredOn(r -> r.itemCode().equals(BidQualityCheckItem.QUALIFICATION_REVIEW.getCode()))
                .singleElement().satisfies(r -> assertThat(r.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW));
    }

    @Test
    void safetyGatePreservesFailuresAndOtherItems() {
        var failure = QualityCheckItemResult.fail(BidQualityCheckItem.QUALIFICATION_REVIEW,
                "财务数值矛盾", "复核", List.of());
        assertThat(SemanticEvidenceBoundary.requireQualificationConfirmation(failure)).isSameAs(failure);
        for (var item : BidQualityCheckItem.values()) {
            var pass = QualityCheckItemResult.pass(item, "已核验", List.of());
            var result = SemanticEvidenceBoundary.reconcile(pass, DocumentContent.empty());
            assertThat(result.status()).as(item.getCode()).isEqualTo(
                    item == BidQualityCheckItem.QUALIFICATION_REVIEW || item == BidQualityCheckItem.QUALIFICATION_VETO
                            ? QualityCheckStatus.MANUAL_REVIEW : QualityCheckStatus.PASS);
        }
    }

    private static QualityCheckItemResult failVerdict(
            final BidQualityCheckItem item, final DocumentContent doc, final String conclusion) {
        var llm = new LlmAdjudicator(prompt -> LlmVerdict.success(
                new LlmVerdict("FAIL", "", conclusion, "请复核对应材料")));
        return llm.adjudicateItem(item, doc, DocumentContent.empty(),
                TenderRequirements.empty(), List.of(), List.of());
    }

    private static DocumentContent.PageContent page(final int number, final String text) {
        return new DocumentContent.PageContent(number, text, List.of());
    }

    private static DocumentContent document(final DocumentContent.PageContent... pages) {
        return new DocumentContent("bid.pdf", "frozen-1117", String.join("\n",
                List.of(pages).stream().map(DocumentContent.PageContent::text).toList()), List.of(pages));
    }
}
