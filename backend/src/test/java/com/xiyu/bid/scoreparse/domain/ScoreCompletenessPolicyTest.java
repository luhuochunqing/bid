package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreCompletenessPolicyTest {

    private ScoreFact createFact(String factId, String parentId, String dim, double weight) {
        ScoreEvidence ev = new ScoreEvidence(
                ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "得" + weight + "分", "c1", 0, 5, "明确分值");
        return new ScoreFact(
                factId, "t1", List.of("c1"), "item " + factId, "1.1", dim,
                BigDecimal.valueOf(weight), ev, null, parentId,
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    @Test
    void evaluate_perfectFacts_returnsAutoApproved() {
        // Parent: 商务 (30), Children: 资信(10), 业绩(20)
        // Leaf: 技术 (70)
        // Total leaf: 10 + 20 + 70 = 100
        ScoreFact parent = createFact("f_parent", null, "商务", 30.0);
        ScoreFact child1 = createFact("f1", "f_parent", "商务-资信", 10.0);
        ScoreFact child2 = createFact("f2", "f_parent", "商务-业绩", 20.0);
        ScoreFact tech = createFact("f3", null, "技术", 70.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(parent, child1, child2, tech), "总分 100 分。");

        assertThat(result.complete()).isTrue();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.fallbackReason()).isNull();
        assertThat(result.leafWeightSum()).isEqualByComparingTo("100.00");
        assertThat(result.leafCount()).isEqualTo(3);
        assertThat(result.parentCount()).isEqualTo(1);
        assertThat(result.defects()).isEmpty();
    }

    @Test
    void evaluate_weightMismatch_returnsReviewRequired() {
        // Leaves total 80 (gap of 20)
        ScoreFact f1 = createFact("f1", null, "商务", 30.0);
        ScoreFact f2 = createFact("f2", null, "技术", 50.0);
        ScoreFact f3 = createFact("f3", null, "价格", 0.0); // 0 weight ignored

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(f1, f2, f3), "满分 100 分。");

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
        assertThat(result.leafWeightSum()).isEqualByComparingTo("80.00");
        assertThat(result.defects()).anyMatch(d -> d.contains("总权重不符预期") && d.contains("差额 20分"));
    }

    @Test
    void evaluate_declaredTotalCustom_matchesCorrectly() {
        // Tender explicitly declares 120 points
        ScoreFact f1 = createFact("f1", null, "商务", 40.0);
        ScoreFact f2 = createFact("f2", null, "技术", 40.0);
        ScoreFact f3 = createFact("f3", null, "价格", 40.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(f1, f2, f3), "本采购项目评标满分120分，详细分值分布见下表：");

        assertThat(result.complete()).isTrue();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.declaredTotalScore()).isEqualByComparingTo("120.00");
        assertThat(result.leafWeightSum()).isEqualByComparingTo("120.00");
    }

    @Test
    void evaluate_categorySumMismatch_returnsReviewRequired() {
        // Parent declares 40, but direct children total 30 (gap of 10)
        ScoreFact parent = createFact("p1", null, "商务", 40.0);
        ScoreFact c1 = createFact("c1", "p1", "商务-资质", 10.0);
        ScoreFact c2 = createFact("c2", "p1", "商务-业绩", 20.0);
        ScoreFact tech = createFact("tech", null, "技术", 70.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(parent, c1, c2, tech), "总分 100 分");

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("CATEGORY_SUM_MISMATCH");
        assertThat(result.defects()).anyMatch(d -> d.contains("大类分值不自洽") && d.contains("声明 40分，但直接子项合计为 30分"));
    }

    @Test
    void evaluate_abnormallyLowItemCount_returnsReviewRequired() {
        // Only 1 leaf item, but text length > 300
        String longText = ("第三章 评标办法前附表 评分标准及细则说明。本项目采用综合评分法，总分100分。"
                + "商务部分满分30分，技术部分满分50分，报价部分满分20分。供应商需严格按照采购文件编制响应文件，"
                + "并在规定时间内提交。").repeat(5);

        ScoreFact f1 = createFact("f1", null, "商务与技术综合项", 100.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(List.of(f1), longText);

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("ABNORMALLY_LOW_COUNT");
        assertThat(result.defects()).anyMatch(d -> d.contains("评分项数量异常偏低") && d.contains("末级项仅 1 项"));
    }

    @Test
    void evaluate_missingEvidence_returnsReviewRequired() {
        ScoreFact f1 = createFact("f1", null, "商务", 40.0);
        ScoreFact f2 = createFact("f2", null, "技术", 30.0);
        // Fact with empty cell ids
        ScoreFact f3 = new ScoreFact(
                "f3", "t1", List.of(), "no evidence item", "1.3", "价格",
                BigDecimal.valueOf(30.0), null, null, null,
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(List.of(f1, f2, f3), "总分100分");

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("MISSING_EVIDENCE");
        assertThat(result.missingEvidenceCount()).isEqualTo(1);
    }

    @Test
    void evaluate_zeroWeightUnboundedFormula_doesNotBlockPositiveFacts() {
        ScoreFact positive = createFact("positive", null, "商务", 100.0);
        ScoreFact formula = new ScoreFact(
                "formula", "t1", List.of("formula-cell"), "折扣率评价 | 价格得分=100-n×差异", "", "折扣率评价",
                null, null, ScoreRule.MAX_SCORE, null,
                "STRUCTURED_TABLE_DETERMINISTIC", 0.0, ScoreFact.ValidationStatus.REVIEW_REQUIRED);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(positive, formula), "满分 100 分。");

        assertThat(result.decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.missingEvidenceCount()).isZero();
        assertThat(result.leafWeightSum()).isEqualByComparingTo("100.00");
    }

    @Test
    void evaluate_declaredTotalPrefersOverallTotalOverLocalFullScorePhrases() {
        ScoreFact f1 = createFact("f1", null, "投标报价", 40.0);
        ScoreFact f2 = createFact("f2", null, "公司综合实力", 6.0);
        ScoreFact f3 = createFact("f3", null, "类似业绩", 10.0);
        ScoreFact f4 = createFact("f4", null, "技术方案", 10.0);
        ScoreFact f5 = createFact("f5", null, "供货方案", 10.0);
        ScoreFact f6 = createFact("f6", null, "人员配备", 6.0);
        ScoreFact f7 = createFact("f7", null, "质量管理方案", 10.0);
        ScoreFact f8 = createFact("f8", null, "售后服务方案", 8.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(f1, f2, f3, f4, f5, f6, f7, f8),
                "报价规则：等于基准价的得满分30分，另一部分得满分10分。合计 得分 满分 （100分）。");

        assertThat(result.declaredTotalScore()).isEqualByComparingTo("100.00");
        assertThat(result.leafWeightSum()).isEqualByComparingTo("100.00");
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
    }

    @Test
    void evaluate_structurallyInvalid_returnsStructureIncomplete() {
        ScoreFact f1 = createFact("f1", null, "商务", 50.0);
        ScoreFact f2 = createFact("f2", null, "技术", 50.0);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(List.of(f1, f2), "总分100分", false);

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.STRUCTURE_INCOMPLETE);
        assertThat(result.fallbackReason()).isEqualTo("STRUCTURE_DEFECT");
    }

    @Test
    void evaluate_emptyFacts_returnsParseFailed() {
        var result = ScoreCompletenessPolicy.evaluateCompleteness(List.of(), "总分100分");

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.PARSE_FAILED);
        assertThat(result.fallbackReason()).isEqualTo("ZERO_VALID_ITEMS");
    }

    // ===== 独立评分表完整性范围区分（分表完整性误判回归） =====

    private ScoreTableSummaryPolicy.DeclaredTotalEvidence structuredTotal(String value) {
        return new ScoreTableSummaryPolicy.DeclaredTotalEvidence(
                new BigDecimal(value), "t1", 99, "t1-total", "满分（" + value + "分）");
    }

    @Test
    void evaluate_structuredSummaryRowTable_belowMinItemCount_longDocumentText_autoApproved() {
        // 独立商务表：2 项 20+20，本表结构化声明 40 分，合计自洽、证据齐全；
        // tenderSectionText 是整份文档正文（>300 字）——文档级上下文不得否决该独立表。
        String longDocumentText = "本项目采用综合评分法。".repeat(60);
        assertThat(longDocumentText.trim().length()).isGreaterThan(300);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(createFact("b1", null, "商务报价", 20.0),
                        createFact("b2", null, "售后服务", 20.0)),
                longDocumentText, true, List.of(structuredTotal("40")));

        assertThat(result.complete()).isTrue();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.fallbackReason())
                .as("独立表不得因整份文档正文长而判 ABNORMALLY_LOW_COUNT")
                .isNotEqualTo("ABNORMALLY_LOW_COUNT");
        assertThat(result.declaredTotalSource())
                .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.STRUCTURED_SUMMARY_ROW);
        assertThat(result.declaredTotalScore()).isEqualByComparingTo("40");
        assertThat(result.leafWeightSum()).isEqualByComparingTo("40");
    }

    @Test
    void evaluate_structuredSummaryRowTable_missingEvidence_stillReviewRequired() {
        // 回归C：独立表合计正确（40=20+20）但其中一项缺少有效分值证据 → 仍必须复核
        String longDocumentText = "本项目采用综合评分法。".repeat(60);
        ScoreFact missingEvidence = new ScoreFact(
                "b2", "t1", List.of(), "no evidence item", "2", "售后服务",
                BigDecimal.valueOf(20.0), null, null, null,
                "STRUCTURED_TABLE_DETERMINISTIC", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(createFact("b1", null, "商务报价", 20.0), missingEvidence),
                longDocumentText, true, List.of(structuredTotal("40")));

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("MISSING_EVIDENCE");
        assertThat(result.missingEvidenceCount()).isEqualTo(1);
    }

    @Test
    void evaluate_structuredSummaryRowTable_declaredButUnderExtracted_stillReviewRequired() {
        // 回归B（domain 层）：声明 40 分只抽到 20 分 → 权重失配必须复核，不得因数量保护放松而放行
        String longDocumentText = "本项目采用综合评分法。".repeat(60);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(createFact("b1", null, "商务报价", 20.0)),
                longDocumentText, true, List.of(structuredTotal("40")));

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("WEIGHT_SUM_MISMATCH");
    }

    @Test
    void evaluate_tenderTextFallback_belowMinItemCount_longText_stillReviewRequired() {
        // 保护不放松：无本表结构化总分声明（正文兜底 TENDER_TEXT）时，数量启发式必须保留
        String longText = ("第三章 评标办法前附表 评分标准及细则说明。本项目采用综合评分法，总分100分。"
                + "商务部分满分30分，技术部分满分50分，报价部分满分20分。供应商需严格按照采购文件编制响应文件，"
                + "并在规定时间内提交。").repeat(5);

        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(createFact("f1", null, "商务与技术综合项", 100.0)), longText, true, List.of());

        assertThat(result.complete()).isFalse();
        assertThat(result.decision()).isEqualTo(ScoreParseDecision.REVIEW_REQUIRED);
        assertThat(result.fallbackReason()).isEqualTo("ABNORMALLY_LOW_COUNT");
        assertThat(result.declaredTotalSource())
                .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.TENDER_TEXT);
    }

    @Test
    void evaluate_default100Fallback_withoutTextAndDeclaration_twoItems_sourceStaysDefault100() {
        // DEFAULT_100 不得视为"该表有明确总分证据"——无正文无声明时保持现有兜底语义
        var result = ScoreCompletenessPolicy.evaluateCompleteness(
                List.of(createFact("f1", null, "商务", 60.0),
                        createFact("f2", null, "技术", 40.0)), null, true, List.of());

        assertThat(result.declaredTotalSource())
                .isEqualTo(ScoreCompletenessPolicy.DeclaredTotalSource.DEFAULT_100);
        assertThat(result.declaredTotalScore()).isEqualByComparingTo("100");
    }
}
