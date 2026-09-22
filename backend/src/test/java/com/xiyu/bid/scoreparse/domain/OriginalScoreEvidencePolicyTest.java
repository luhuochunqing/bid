package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreEvidencePolicyTest {
    @Test void bodyItemMaximumCannotInventSchemeTotal() {
        var source = artifact("本项目评分办法", List.of(
                cell("n1", 0, 0, "证书"), cell("r1", 0, 1, "本项目该评分项满分10分"),
                cell("w1", 0, 2, "10分")));
        assertThat(OriginalScoringContext.resolve(source,
                "## 本项目评分办法\n本项目该评分项满分10分。").total().value()).isNull();
    }

    @Test void unscopedBusinessTableSubtotalCannotInventSchemeTotal() {
        var source = artifact("商务评分表", List.of(
                cell("h1", 0, 0, "评分项目"), cell("h2", 0, 1, "评分规则"),
                cell("n1", 1, 0, "履约能力"), cell("r1", 1, 1, "履约能力最高得20分"),
                cell("w1", 1, 2, "20分"), cell("sum-name", 2, 0, "合计"),
                cell("sum-value", 2, 2, "20分")));
        assertThat(OriginalScoringContext.resolve(source, "").total().value()).isNull();
    }

    @Test void unrelatedQualificationTableFromOtherPackageDoesNotCreateSchemeConflict() {
        var first = artifact("第一标包评分办法", List.of(
                cell("total", 0, 0, "本标包评分总分100分"),
                cell("name", 1, 0, "技术方案"), cell("rule", 1, 1, "技术方案最高得100分"),
                cell("weight", 1, 2, "100分"))).tables().get(0);
        var unrelated = new DocumentArtifact.CanonicalTable("t-other", 2, "第二标包 / 资格审查", 1, 1,
                List.of(), List.of(cell("qualification", 0, 0, "营业执照")), false);
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-qualification", "pdf", "2.0"),
                List.of(first, unrelated), List.of());
        assertThat(OriginalScoringContext.resolve(source, "").total().value()).isEqualByComparingTo("100");
    }
    @Test void itemWordingCannotBecomeConflictingSchemeTotal() {
        var source = artifact("本项目评分办法", List.of(
                cell("n1", 0, 0, "销售额"), cell("r1", 0, 1, "本项目该评分项满分10分"),
                cell("w1", 0, 2, "10分"), cell("n2", 1, 0, "技术方案"),
                cell("r2", 1, 1, "技术方案最高得90分"), cell("w2", 1, 2, "90分")));
        var context = OriginalScoringContext.resolve(source, "本项目评分总分100分。");
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().source()).isEqualTo("TENDER_TEXT");
    }

    @Test void loneBusinessSubtotalIsNotSchemeTotal() {
        var source = artifact("本项目评分办法 / 商务", List.of(
                cell("h1", 0, 0, "评分项目"), cell("h2", 0, 1, "评分规则"),
                cell("n1", 1, 0, "履约能力"), cell("r1", 1, 1, "履约能力最高得20分"),
                cell("w1", 1, 2, "20分"), cell("sum-name", 2, 0, "合计"),
                cell("sum-value", 2, 2, "20分")));
        assertThat(OriginalScoringContext.resolve(source, "").total().value()).isNull();
    }

    @Test void differentPackageScoringChildCannotJoinFirstPackage() {
        var first = artifact("第一标包评分办法", List.of(
                cell("a-total", 0, 0, "第一标包评分总分100分"),
                cell("a-name", 1, 0, "技术方案"), cell("a-rule", 1, 1, "技术方案最高得100分"),
                cell("a-weight", 1, 2, "100分"))).tables().get(0);
        var second = new DocumentArtifact.CanonicalTable("t-other", 2, "第二标包 / 评分规则", 2, 2,
                List.of(), List.of(cell("b-name", 0, 0, "实施方案"),
                cell("b-rule", 0, 1, "实施方案最高得80分")), true);
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-packages", "pdf", "2.0"),
                List.of(first, second), List.of());
        var context = OriginalScoringContext.resolve(source, "");
        assertThat(context.total().value()).isNull();
        assertThat(context.total().problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_SCHEME_AMBIGUOUS");
    }
    @Test void independentSummaryTableKeepsHundredWhenModelOmitsTechnicalTable() {
        var report = OriginalScoreEvidencePolicy.verifyModel(schemeAcrossThreeTables(), schemeText(),
                List.of(schemeFact("1", "履约能力", "20", "t-business", "b-rule",
                        "满足原文列明条件，最高得20分。")), new BigDecimal("20"));
        assertThat(report.total().value()).isEqualByComparingTo("100");
        assertThat(report.publishable()).isFalse();
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("MODEL_TOTAL_SOURCE_MISMATCH");
    }

    @Test void independentSummaryTableAllowsBothDetailsWithoutCountingSummaryTwice() {
        var report = OriginalScoreEvidencePolicy.verifyModel(schemeAcrossThreeTables(), schemeText(),
                List.of(schemeFact("1", "履约能力", "20", "t-business", "b-rule",
                                "满足原文列明条件，最高得20分。"),
                        schemeFact("2", "实施方案", "80", "t-technical", "t-rule",
                                "按原文方案要求评议，最高得80分。")), new BigDecimal("100"));
        assertThat(report.total().value()).isEqualByComparingTo("100");
        assertThat(report.publishable()).isTrue();
    }

    @Test void emptyModelFactsDoNotEraseIndependentOriginalTotal() {
        var report = OriginalScoreEvidencePolicy.verifyModel(schemeAcrossThreeTables(), schemeText(),
                List.of(), new BigDecimal("100"));
        assertThat(report.total().value()).isEqualByComparingTo("100");
        assertThat(report.publishable()).isFalse();
    }

    @Test void equivalentTenderLayoutRetainsSchemeHeadingAcrossPunctuation() {
        var source = artifact("本项目评分办法", List.of(
                cell("n1", 0, 0, "销售额"), cell("r1", 0, 1, "销售额达到1亿元得10分"),
                cell("w1", 0, 2, "10分"), cell("n2", 1, 0, "技术方案"),
                cell("r2", 1, 1, "技术方案最高得90分"), cell("w2", 1, 2, "90分")));
        var facts = List.of(fact("1", "销售额", "10", "销售额达到1亿元得10分", "r1"),
                fact("2", "技术方案", "90", "技术方案最高得90分", "r2"));
        for (String text : List.of("本项目评分总分100分。", "本项目采用综合评分法，总分100分。",
                "## 本项目评分办法\n总分100分。")) {
            var report = OriginalScoreEvidencePolicy.verifyModel(source, text, facts, new BigDecimal("100"));
            assertThat(report.total().value()).as(text).isEqualByComparingTo("100");
            assertThat(report.publishable()).as(text).isTrue();
        }
    }

    @Test void totalAndItemMaximumWithWeiConnectorRemainPublishable() {
        var source = artifact("评分办法", List.of(
                cell("n1", 0, 0, "证书"), cell("r1", 0, 1, "证书齐备，本项满分为10分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source,
                "本项目评分标准总分为10分。",
                List.of(fact("1", "证书", "10", "证书齐备，本项满分为10分", "r1")),
                new BigDecimal("10"));
        assertThat(report.publishable()).isTrue();
    }

    @Test void bodyTotalOutsideTableSupportsEightyPointPlan() {
        var source = artifact("评分办法", List.of(
                cell("n1", 0, 0, "商务服务"), cell("r1", 0, 1, "售后保障最高得30分"), cell("w1", 0, 2, "30分"),
                cell("n2", 1, 0, "技术方案"), cell("r2", 1, 1, "技术方案最高得50分"), cell("w2", 1, 2, "50分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "本项目评分总分：80分。",
                List.of(fact("1", "商务服务", "30", "售后保障最高得30分", "r1"),
                        fact("2", "技术方案", "50", "技术方案最高得50分", "r2")), new BigDecimal("80"));
        assertThat(report.publishable()).isTrue();
        assertThat(report.total().value()).isEqualByComparingTo("80");
        assertThat(report.total().source()).isEqualTo("TENDER_TEXT");
    }

    @Test void independentSingleTableEightyPointSummaryIsWholePlanTotal() {
        var source = artifact("本项目评分办法", List.of(
                cell("h1", 0, 0, "评分项目"), cell("h2", 0, 1, "评分规则"), cell("h3", 0, 2, "分值"),
                cell("n1", 1, 0, "商务服务"), cell("r1", 1, 1, "商务服务最高得30分"), cell("w1", 1, 2, "30分"),
                cell("n2", 2, 0, "技术方案"), cell("r2", 2, 1, "技术方案最高得50分"), cell("w2", 2, 2, "50分"),
                cell("summary", 3, 0, "合计"), cell("sum", 3, 2, "80分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "商务服务", "30", "商务服务最高得30分", "r1"),
                        fact("2", "技术方案", "50", "技术方案最高得50分", "r2")), new BigDecimal("80"));
        assertThat(report.publishable()).isTrue();
        assertThat(report.total().value()).isEqualByComparingTo("80");
    }

    @Test void conflictingSingleTableSummariesAreNotLastWriteWins() {
        var source = artifact("本项目评分办法", List.of(
                cell("h1", 0, 0, "评分项目"), cell("h2", 0, 1, "评分规则"), cell("h3", 0, 2, "分值"),
                cell("n1", 1, 0, "技术方案"), cell("r1", 1, 1, "技术方案最高得100分"),
                cell("w1", 1, 2, "100分"),
                cell("summary1", 2, 0, "合计"), cell("sum1", 2, 2, "100分"),
                cell("summary2", 3, 0, "总计"), cell("sum2", 3, 2, "80分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "技术方案", "100", "技术方案最高得100分", "r1")),
                new BigDecimal("100"));
        assertThat(report.publishable()).isFalse();
        assertThat(report.total().value()).isNull();
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_TOTAL_CONFLICT");
    }

    @Test void twoIndependentPackagesNeedExplicitTaskScope() {
        var first = artifact("第一标包评分办法", List.of(
                cell("a-total", 0, 0, "第一标包评分总分100分"),
                cell("a-name", 1, 0, "技术方案"), cell("a-rule", 1, 1, "技术方案最高得100分"),
                cell("a-weight", 1, 2, "100分"))).tables().get(0);
        var second = new DocumentArtifact.CanonicalTable("t-other", 2, "第二标包评分办法", 2, 2,
                List.of(), List.of(cell("b-total", 0, 0, "第二标包评分总分80分"),
                        cell("b-name", 1, 0, "技术方案"), cell("b-rule", 1, 1, "技术方案最高得80分")), true);
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-packages", "pdf", "2.0"),
                List.of(first, second), List.of());
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(new MultimodalScoreFact("第一标包评分办法", "1", "", "技术方案", new BigDecimal("100"),
                        "技术方案最高得100分", "OBJECTIVE", 3, "t1", List.of("a-rule"),
                        "技术方案最高得100分", 0.9)), new BigDecimal("100"));
        assertThat(report.publishable()).isFalse();
        assertThat(report.total().value()).isNull();
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_SCHEME_AMBIGUOUS");
    }

    @Test void unrelatedTableWithLargerTotalCannotOverrideSchemeOrSupplyItem() {
        var schemeTable = artifact("本项目评分办法", List.of(
                cell("h1", 0, 0, "评分项目"), cell("h2", 0, 1, "评分规则"),
                cell("n1", 1, 0, "技术方案"), cell("r1", 1, 1, "技术方案最高得100分"),
                cell("w1", 1, 2, "100分"))).tables().get(0);
        var unrelated = new DocumentArtifact.CanonicalTable("t-other", 2, "资格审查表", 2, 2,
                List.of(), List.of(cell("u1", 0, 0, "合计"), cell("u2", 0, 1, "本项目资格审查满分1000分"),
                        cell("u3", 1, 0, "技术方案"), cell("u4", 1, 1, "技术方案最高得100分")), false);
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-unrelated", "pdf", "2.0"),
                List.of(schemeTable, unrelated), List.of());
        var valid = OriginalScoreEvidencePolicy.verifyModel(source, "本项目评分总分100分。",
                List.of(fact("1", "技术方案", "100", "技术方案最高得100分", "r1")), new BigDecimal("100"));
        assertThat(valid.publishable()).isTrue();
        assertThat(valid.total().value()).isEqualByComparingTo("100");
        var wrongTable = OriginalScoreEvidencePolicy.verifyModel(source, "本项目评分总分100分。",
                List.of(new MultimodalScoreFact("本项目评分办法", "1", "", "技术方案", new BigDecimal("100"),
                        "技术方案最高得100分", "OBJECTIVE", 2, "t-other", List.of("u4"),
                        "技术方案最高得100分", 0.9)), new BigDecimal("100"));
        assertThat(wrongTable.publishable()).isFalse();
        assertThat(wrongTable.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_SCHEME_SCOPE_MISMATCH");
    }

    @Test void twoPackageHeadingsInBodyDoNotSilentlySelectFirstPlan() {
        var source = artifact("", List.of(
                cell("n1", 0, 0, "技术方案"), cell("r1", 0, 1, "技术方案最高得100分"),
                cell("w1", 0, 2, "100分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source,
                "## 第一标包评分办法\n总分100分。\n## 第二标包评分办法\n总分80分。",
                List.of(fact("1", "技术方案", "100", "技术方案最高得100分", "r1")),
                new BigDecimal("100"));
        assertThat(report.publishable()).isFalse();
        assertThat(report.total().value()).isNull();
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_SCHEME_AMBIGUOUS");
    }

    @Test void duplicateWholeTotalAndCategorySubtotalsAreNotConflicts() {
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分100分，其中商务部分满分10分，技术部分满分90分"),
                cell("n1", 1, 0, "销售额"), cell("r1", 1, 1, "销售额达到1亿元得10分"), cell("w1", 1, 2, "10分"),
                cell("n2", 2, 0, "技术方案"), cell("r2", 2, 1, "技术方案最高得90分"), cell("w2", 2, 2, "90分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source,
                "本项目评分总分100分。\n本项目评分总分100.0分。其他标包评分总分200分。",
                List.of(fact("1", "销售额", "10", "销售额达到1亿元得10分", "r1"),
                        fact("2", "技术方案", "90", "技术方案最高得90分", "r2")), new BigDecimal("100"));
        assertThat(report.publishable()).isTrue();
        assertThat(report.total().value()).isEqualByComparingTo("100");
    }

    @Test void conflictingOrMissingOriginalTotalCannotPublish() {
        var cells = List.of(cell("n1", 0, 0, "销售额"), cell("r1", 0, 1, "销售额得100分"),
                cell("w1", 0, 2, "100分"));
        var fact = fact("1", "销售额", "100", "销售额得100分", "r1");
        var missing = OriginalScoreEvidencePolicy.verifyModel(artifact("评分办法", cells), "",
                List.of(fact), new BigDecimal("100"));
        assertThat(missing.publishable()).isFalse();
        assertThat(missing.total().value()).isNull();
        assertThat(missing.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_TOTAL_UNKNOWN");

        var conflicting = OriginalScoreEvidencePolicy.verifyModel(artifact("评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分100分"),
                cell("n1", 1, 0, "销售额"), cell("r1", 1, 1, "销售额得100分"), cell("w1", 1, 2, "100分"))),
                "本项目评分总分80分", List.of(fact), new BigDecimal("100"));
        assertThat(conflicting.publishable()).isFalse();
        assertThat(conflicting.total().value()).isNull();
        assertThat(conflicting.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_TOTAL_CONFLICT");
    }

    @Test void unknownOriginalTotalCanBeProjectedWithoutInventingHundred() throws Exception {
        var snapshot = new ScoreCompletenessPolicy.ScoreCompletenessResult(false,
                ScoreParseDecision.REVIEW_REQUIRED, "ORIGINAL_TOTAL_UNKNOWN", null,
                ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN, new BigDecimal("100"),
                1, 0, 0, List.of("ORIGINAL_TOTAL_UNKNOWN"), List.of(), List.of()).toReviewSnapshot();
        assertThat(snapshot.get("declaredTotalScore")).isNull();
        assertThat(snapshot.get("discrepancy")).isNull();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snapshot))
                .contains("\"declaredTotalScore\":null");
    }

    @Test void sourceWeightUsesItemMaximumNotIncrementOrAmountThreshold() {
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分20分"),
                cell("n1", 1, 0, "证书"), cell("r1", 1, 1, "每份证书得2分，最高10分"), cell("w1", 1, 2, "10分"),
                cell("n2", 2, 0, "销售额"), cell("r2", 2, 1, "合同金额达到100万元得10分"), cell("w2", 2, 2, "10.0分")));
        var valid = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "证书", "10.0", "每份证书得2分，最高10分", "r1"),
                        fact("2", "销售额", "10", "合同金额达到100万元得10分", "r2")), new BigDecimal("20"));
        assertThat(valid.publishable()).isTrue();

        var otherItem = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "销售额", "10", "每份证书得2分，最高10分", "r1")), new BigDecimal("20"));
        assertThat(otherItem.publishable()).isFalse();
        assertThat(otherItem.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_SOURCE_SCOPE_MISMATCH");
    }

    @Test void perCertificateIncrementWithoutCapIsNotItemMaximum() {
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分2分"),
                cell("n1", 1, 0, "证书"), cell("r1", 1, 1, "每份证书得2分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "证书", "2", "每份证书得2分", "r1")), new BigDecimal("2"));
        assertThat(report.publishable()).isFalse();
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }

    private static MultimodalScoreFact fact(String code, String name, String weight, String rule, String cellId) {
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, "t1", List.of(cellId), rule, 0.9);
    }

    private static String schemeText() {
        return "# 本项目评分办法\n评分方案汇总表\n| 汇总项 | 分值 |\n| 合计 | 满分100分 |\n"
                + "商务明细表\n| 序号 | 评分因素 | 评分规则 | 分值 |\n"
                + "| 1 | 履约能力 | 满足原文列明条件，最高得20分。 | 20 |\n"
                + "| 合计 | | | 20 |\n技术明细表\n| 序号 | 评分因素 | 评分规则 | 分值 |\n"
                + "| 2 | 实施方案 | 按原文方案要求评议，最高得80分。 | 80 |\n| 合计 | | | 80 |";
    }

    private static MultimodalScoreFact schemeFact(String code, String name, String weight,
                                                   String tableId, String cellId, String rule) {
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, tableId, List.of(cellId), rule, 0.9);
    }

    private static DocumentArtifact schemeAcrossThreeTables() {
        var summary = new DocumentArtifact.CanonicalTable("t-summary", 3, "本项目评分办法 / 评分方案汇总表",
                2, 2, List.of(), List.of(cell("s-h1", 0, 0, "汇总项"),
                cell("s-h2", 0, 1, "分值"), cell("s-total", 1, 0, "合计"),
                cell("s-value", 1, 1, "满分100分")), false);
        var business = new DocumentArtifact.CanonicalTable("t-business", 3, "本项目评分办法 / 商务明细表",
                3, 4, List.of(), List.of(cell("b-h1", 0, 0, "序号"), cell("b-h2", 0, 1, "评分因素"),
                cell("b-h3", 0, 2, "评分规则"), cell("b-h4", 0, 3, "分值"),
                cell("b-code", 1, 0, "1"), cell("b-name", 1, 1, "履约能力"),
                cell("b-rule", 1, 2, "满足原文列明条件，最高得20分。"), cell("b-score", 1, 3, "20"),
                cell("b-total", 2, 0, "合计"), cell("b-sum", 2, 3, "20")), true);
        var technical = new DocumentArtifact.CanonicalTable("t-technical", 3, "本项目评分办法 / 技术明细表",
                3, 4, List.of(), List.of(cell("t-h1", 0, 0, "序号"), cell("t-h2", 0, 1, "评分因素"),
                cell("t-h3", 0, 2, "评分规则"), cell("t-h4", 0, 3, "分值"),
                cell("t-code", 1, 0, "2"), cell("t-name", 1, 1, "实施方案"),
                cell("t-rule", 1, 2, "按原文方案要求评议，最高得80分。"), cell("t-score", 1, 3, "80"),
                cell("t-total", 2, 0, "合计"), cell("t-sum", 2, 3, "80")), true);
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(summary, business, technical), List.of());
    }

    private static DocumentArtifact artifact(String section,
                                             List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, section, 3, 3,
                        List.of(), cells, true)), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, text, text,
                1, 1, false, "");
    }
}
