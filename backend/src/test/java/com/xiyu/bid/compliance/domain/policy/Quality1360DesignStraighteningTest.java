package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.*;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Quality1360DesignStraighteningTest {
    @Test
    void eachSensitiveHitUsesItsOwnClauseInsteadOfTheFirstPredicate() {
        assertThat(veto("我方承诺不存在以下情形：\n1. 我司未参与围标，但我司已进行违法分包。").status())
                .isEqualTo(QualityCheckStatus.FAIL);
        assertThat(veto("我方承诺不存在以下情形：\n1. 我司已完成自查，未参与围标。").status())
                .isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void aTopicAndLaterStatementDoNotProveTheSensitiveFact() {
        var result = veto("我方承诺不存在以下情形：\n1. 弄虚作假：我司相关材料情况待核实。");
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).startsWith("[AMBIGUOUS]");
        assertThat(veto("我方承诺不存在以下情形：\n1. 弄虚作假：我司已完成材料自查。").status())
                .isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void unparsedObligationStaysUnknownThroughFusion() {
        var price = DeterministicPriceExtractor.extract("报价表仅为资料目录标题。");
        assertThat(price.requireBidOpeningTable()).isNull();
        assertThat(price.requireItemizedList()).isNull();
        var unknown = new PriceRequirement(true, null, null, null, null, null, "未知");
        var fused = PriceRequirement.fuseWithDeterministic(unknown, price);
        assertThat(fused.requireBidOpeningTable()).isNull();
        assertThat(fused.requireItemizedList()).isNull();
    }

    @Test
    void quotationEvidenceLocatesTheSelectedTextInsteadOfAnySimilarInstruction() {
        String selected = "在系统报价表中填报不含税折扣率";
        var tender = new DocumentContent("tender.pdf", "h", selected, List.of(
                new DocumentContent.PageContent(1, "在系统报价表中填写含税折扣率", List.of()),
                new DocumentContent.PageContent(80, selected, List.of())));
        var result = PricingSubmissionPolicy.reconcile(QualityCheckItemResult.fail(
                BidQualityCheckItem.BID_OPENING_TABLE, "缺表", "核对", List.of()), tender,
                DeterministicPriceExtractor.extract(selected));
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.conclusion()).startsWith("[EVIDENCE_INSUFFICIENT]");
        assertThat(result.evidences()).singleElement().satisfies(e -> {
            assertThat(e.pageNumber()).isEqualTo(80);
            assertThat(e.quote()).isEqualTo(selected);
        });
    }

    @Test
    void scoringBlankRequiresConfirmationRegardlessOfInstructionSyntax() {
        var bid = DocumentContent.fromText("bid.docx", "b", "响应时间：_____小时。");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, requirements("须提供服务协议并填写响应时间"))
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间"))
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void anInstructionInsideAnUnresolvedAnnotationCannotBecomeMandatory() {
        var bid = DocumentContent.fromText("bid.docx", "b", "响应时间：_____小时。");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid,
                requirements("须提供服务协议（参考样例；须填写响应时间；以上均供参考）"))
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void responsivenessWithoutVerifiableFactsDoesNotSpendAModelCallThenDowngrade() {
        var calls = new AtomicInteger();
        var llm = new LlmAdjudicator(prompt -> {
            if (prompt.contains("「" + BidQualityCheckItem.RESPONSIVENESS_REVIEW.getTitle() + "」")) calls.incrementAndGet();
            return LlmVerdict.success(new LlmVerdict("PASS", "", "模型认为满足", ""));
        });
        var results = BidDocumentQualityEngine.evaluateAll(DocumentContent.fromText("bid.docx", "b", "项目说明。"),
                DocumentContent.fromText("tender.pdf", "t", "招标要求尚待确认。"), List.of(),
                TenderRequirements.empty(), Set.of(), llm, (p, s) -> { });
        assertThat(results.stream().filter(r -> r.itemCode().equals("RESPONSIVENESS_REVIEW")).findFirst()
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(calls.get()).isZero();
    }

    @Test
    void sameNamedBlankInFutureContractDoesNotProveMissingActualResponse() {
        var bid = DocumentContent.fromText("bid.docx", "b",
                "响应时间：2小时。\n附件：合同示例（未来订单填写）\n响应时间：_____小时。");
        var result = ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间")).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).singleElement().satisfies(e ->
                assertThat(e.quote()).contains("_____"));
        assertThat(ScoringResponsePolicy.checkFirstStage(
                DocumentContent.fromText("bid.docx", "b", "响应时间：_____小时。"),
                requirements("须填写响应时间")).orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void ordinaryContractFieldsDoNotRestoreProofOfActualScoringUse() {
        var bid = DocumentContent.fromText("bid.docx", "b",
                "响应时间：2小时。\n附件：合同示例（未来订单填写）\n合同编号：HT-001\n响应时间：_____小时。");
        var result = ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间")).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).singleElement().satisfies(e -> assertThat(e.quote()).contains("_____"));
    }

    @Test
    void contractSampleCueSurvivesPaginationWithoutInventingItsEnd() {
        var bid = new DocumentContent("bid.docx", "b", "", List.of(
                new DocumentContent.PageContent(3, "附件：合同模板（未来订单填写）", List.of()),
                new DocumentContent.PageContent(4, "合同编号：HT-001\n响应时间：_____小时。", List.of())));
        var result = ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间")).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).singleElement().satisfies(e -> assertThat(e.pageNumber()).isEqualTo(4));
    }

    @Test
    void earlierScoringBlankAlsoRequiresConfirmation() {
        var bid = DocumentContent.fromText("bid.docx", "b",
                "响应时间：_____小时。\n附件：合同示例（未来订单填写）\n合同编号：HT-001");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间"))
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void multipleFieldOccurrencesNeedBindingEvenWithoutAnySampleTitle() {
        var bid = DocumentContent.fromText("bid.docx", "b", "响应时间：2小时。\n资料编号：HT-001\n响应时间：_____小时。");
        assertThat(ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间"))
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        var bareHeadings = DocumentContent.fromText("bid.docx", "b",
                "技术方案\n已提供实施交付内容。\n资料编号：HT-001\n技术方案\n_____");
        assertThat(ScoringResponsePolicy.checkFirstStage(bareHeadings, requirements("须填写技术方案"))
                .orElseThrow().status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void repeatedFieldOnAnotherPageDoesNotAcquireTheFirstFieldsObligation() {
        var bid = new DocumentContent("bid.docx", "b", "", List.of(
                new DocumentContent.PageContent(3, "响应时间：2小时。", List.of()),
                new DocumentContent.PageContent(80, "资料编号：HT-001\n响应时间：_____小时。", List.of())));
        var result = ScoringResponsePolicy.checkFirstStage(bid, requirements("须填写响应时间")).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(result.evidences()).singleElement().satisfies(e -> assertThat(e.pageNumber()).isEqualTo(80));
    }

    @Test
    void suffixOfComplexDenialCannotProveAbsenceOfViolation() {
        assertThat(veto("我司并非未参与围标。").status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
        assertThat(veto("我司未参与围标。").status()).isEqualTo(QualityCheckStatus.PASS);
        assertThat(veto("我司已参与围标。").status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    private static QualityCheckItemResult veto(String text) {
        return SubItemAndAttachmentRules.checkVetoKeyword(DocumentContent.fromText("bid.docx", "b", text));
    }
    private static TenderRequirements requirements(String text) {
        return new TenderRequirements(List.of(new TenderRequirements.ScoringItem("服务方案", 5.0, text)),
                List.of(), List.of(), List.of(), List.of(), false);
    }
}
