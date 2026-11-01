package com.xiyu.bid.compliance.domain.policy;

import com.xiyu.bid.compliance.domain.DocumentContent;
import com.xiyu.bid.compliance.domain.BidQualityCheckItem;
import com.xiyu.bid.compliance.domain.QualityCheckStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class Report147ResponsivenessRegressionTest {
    private static final String RULE = "注：允许正偏差，但不允许负偏差。";

    @Test
    void realTenderRuleAndBidResponseAreNotAnAdmittedNegativeDeviation() {
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var bid = DocumentContent.fromText("bid.docx", "bid", """
                商务和技术偏差表
                | 序号 | 招标文件章节及条款号 | 投标文件章节及条款号 | 偏差说明 |
                | 1 | 招标文件章节及条款号 | 西域承诺完全响应招标文件的全部要求 | 无偏差 |
                | 2 | | | |
                | …… | | | |
                注：允许正偏差，但不允许负偏差。
                """);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of());

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isNotEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.orElseThrow().evidences()).anyMatch(e -> "BID".equals(e.sourceType())
                && e.quote().contains("西域承诺完全响应招标文件的全部要求"));
        assertThat(result.orElseThrow().evidences()).anyMatch(e -> "TENDER".equals(e.sourceType())
                && e.quote().contains("不允许负偏差"));
    }

    @Test
    void prohibitionInTenderInstructionsIsNotProofOfBidderDeviation() {
        var bid = DocumentContent.fromText("bid.docx", "report-147-sample",
                "注：允许正偏差，但不允许负偏差。");
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElse(null);

        assertThat(result == null || result.status() != QualityCheckStatus.FAIL)
                .as("规则引用不能证明投标人存在负偏差")
                .isTrue();
    }

    @Test
    void genuineBidderDeviationAfterCopiedRuleRemainsFailure() {
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var bid = DocumentContent.fromText("bid.docx", "bid", RULE + "我方交货期存在负偏差，实际45天。");
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).anyMatch(e -> e.quote().contains("我方交货期存在负偏差"));
        assertThat(result.evidences()).allMatch(e -> bid.fullText().contains(e.quote()));
    }

    @Test
    void actualDeviationOnSamePageAsCitationIsNotExempted() {
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var bid = DocumentContent.fromText("bid.docx", "bid",
                RULE + "我方交货期存在负偏离，但招标文件不允许负偏离。");
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).anyMatch(e -> e.quote().contains("我方交货期存在负偏离"));
    }

    @Test
    void copiedRuleAndActualDeviationWithinOneSentenceAreNotExempted() {
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var bid = DocumentContent.fromText("bid.docx", "bid",
                "注：允许正偏差，但不允许负偏差，我方交货期存在负偏离，实际45天。");
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void completeCopiedRuleIncludingDefinitionIsNotADeviation() {
        String rule = RULE + "对于投标人列为正偏差的（正偏差是指应答的条件高于招标文件要求，负偏差是指应答的条件低于招标文件要求）请在上表中逐项列明并明确偏差详细情况。";
        var tender = DocumentContent.fromText("tender.docx", "tender", rule);
        var bid = DocumentContent.fromText("bid.docx", "bid", rule);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.MANUAL_REVIEW);
    }

    @Test
    void tabSeparatedNegativeRowEvidenceIsVerbatim() {
        String row = "2.1\t30天\t我方45天\t负偏离\t产能限制";
        var bid = DocumentContent.fromText("bid.docx", "bid",
                "商务偏离表\n条款\t招标要求\t投标响应\t偏离情况\t说明\n" + row);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, DocumentContent.empty(), TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).anyMatch(e -> row.equals(e.quote()) && e.pageNumber() != null);
    }

    @Test
    void citationDoesNotExemptMissingDeviationTableBody() {
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var bid = DocumentContent.fromText("bid.docx", "bid", RULE + "\n目录\n商务偏离表......12");
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    void sourceLineBreakMustNotProduceInventedTenderQuote() {
        var tender = DocumentContent.fromText("tender.docx", "tender", "不允许\n负偏差。");
        var bid = DocumentContent.fromText("bid.docx", "bid", "不允许负偏差。");
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.evidences()).allMatch(e -> !"TENDER".equals(e.sourceType())
                || tender.fullText().contains(e.quote()));
    }

    @Test
    void missingTenderCannotTurnAnUnattributedRuleIntoAFailure() {
        var bid = DocumentContent.fromText("bid.docx", "bid", RULE);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, DocumentContent.empty(), TenderRequirements.missingSentinel(), List.of(), Set.of());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void bidderAcknowledgingAProhibitionIsNotADeviation() {
        var bid = DocumentContent.fromText("bid.docx", "bid", "我方知悉不允许负偏差。");
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, DocumentContent.empty(), TenderRequirements.missingSentinel(), List.of(), Set.of());
        assertThat(result).isPresent();
        assertThat(result.orElseThrow().status()).isEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    @EnabledIfSystemProperty(named = "xiyu.responsiveness.known-defects", matches = "true")
    void bidderSuppliedRequirementMustNotReplaceTenderRequirement() {
        var tender = DocumentContent.fromText("tender.docx", "tender", "真实招标要求交货期30天。");
        var bid = DocumentContent.fromText("bid.docx", "bid", """
                商务偏离表
                | 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |
                | 1 | 45天 | 45天 | 无偏离 | 满足要求 |
                """);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
    }

    @Test
    void negativeRowEvidenceMustPointToActualRowRatherThanEarlierRule() {
        var tender = DocumentContent.fromText("tender.docx", "tender", RULE);
        var bid = new DocumentContent("bid.docx", "bid", RULE + "\n实际负偏离表", List.of(
                new DocumentContent.PageContent(1, RULE, List.of()),
                new DocumentContent.PageContent(2, """
                        商务偏离表
                        | 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |
                        | 2.1 | 30天 | 我方45天 | 负偏离 | 产能限制 |
                        """, List.of())));
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isEqualTo(QualityCheckStatus.FAIL);
        assertThat(result.evidences()).anyMatch(e -> Integer.valueOf(2).equals(e.pageNumber())
                && e.quote().contains("我方45天"));
    }

    @Test
    void copiedRuleAcrossTenderPagesMustNotBecomeBidderDeviation() {
        var tender = new DocumentContent("tender.docx", "tender", RULE, List.of(
                new DocumentContent.PageContent(1, "注：允许正偏差，", List.of()),
                new DocumentContent.PageContent(2, "但不允许负偏差。", List.of())));
        var bid = DocumentContent.fromText("bid.docx", "bid", RULE);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.FAIL);
    }

    @Test
    @EnabledIfSystemProperty(named = "xiyu.responsiveness.known-defects", matches = "true")
    void bidderValueIsNotMatchedBySubstringOfDifferentTenderValue() {
        var tender = DocumentContent.fromText("tender.docx", "tender", "质保期不得少于15天。");
        var bid = DocumentContent.fromText("bid.docx", "bid", """
                商务偏离表
                | 条款 | 招标要求 | 投标响应 | 偏离情况 | 说明 |
                | 1 | 5天 | 5天 | 无偏离 | 满足要求 |
                """);
        var result = SemanticRulesLane.deterministicOutcome(BidQualityCheckItem.RESPONSIVENESS_REVIEW,
                bid, tender, TenderRequirements.missingSentinel(), List.of(), Set.of()).orElseThrow();
        assertThat(result.status()).isNotEqualTo(QualityCheckStatus.PASS);
    }
}
