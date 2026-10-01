package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreEvidencePolicyTest {
    @Test void categorySubtotalQuoteProvesWeightConsistentlyForTableLocator() {
        var table = new DocumentArtifact.CanonicalTable("t-category", 33, "第三章 评审办法", 1, 1,
                List.of(), List.of(cell("category", 0, 0, "（1）商务部分：15分")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-category", "PDF", "2.2"),
                List.of(table), List.of());
        var claim = new ScoreEvidenceClaim("fact", "fact", "t-category", "商务", List.of("category"),
                List.of("category"), new BigDecimal("15"), false, true,
                "（1）商务部分：15分", "", "", "", ChildAggregation.SUM);
        var problems = new java.util.ArrayList<OriginalScoreEvidencePolicy.Problem>();

        OriginalScoreWeightEvidencePolicy.verifyWeight(artifact, claim, problems, java.util.Map.of());

        assertThat(problems).isEmpty();
    }

    @Test void splitDocxRunsStillExposeExplicitWholeDocumentTotal() {
        var source = artifact("评分办法", List.of(
                cell("n1", 0, 0, "业绩"), cell("r1", 0, 1, "满足条件得10分"), cell("w1", 0, 2, "10分")));
        var context = OriginalScoringContext.resolve(source,
                "本项目总分\n为\u00a0100\u00a0分，由商务部分 40 分、技术部分 30 分和响应报价 30 分组成。");
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().source()).isEqualTo("TENDER_TEXT");
    }

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
                .contains("ORIGINAL_ELIGIBLE_FACTS_MISSING");
        assertThat(wrongTable.qualifications()).singleElement()
                .satisfies(qualification -> assertThat(qualification.candidateEligible()).isFalse());
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

    @Test void renamedClaimIsRefusedWhenAnotherFactLegitimatelyBearsThatName() {
        // 评审反例："供应商履约业绩"冒充"供应商履约能力"——即使能力行内锚点唯一，
        // 主张项名撞上另一事实的原文锚点名 → 池级歧义 → 不自动通过，继续人工复核
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分10分"),
                cell("r1", 1, 1, "供应商履约能力（满分5分）按季度评价"), cell("w1", 1, 2, "5分"),
                cell("r2", 2, 1, "供应商履约业绩（满分5分）按合同评价"), cell("w2", 2, 2, "5分")));
        var genuine = fact("2", "供应商履约业绩", "5", "供应商履约业绩（满分5分）按合同评价", "r2");
        var impostor = fact("1", "供应商履约业绩", "5", "供应商履约能力（满分5分）按季度评价", "r1");
        var verification = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(impostor, genuine), new BigDecimal("10"));
        assertThat(verification.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_SOURCE_SCOPE_MISMATCH");
        assertThat(verification.publishable()).isFalse();
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

    @Test void publishedDimensionAndAliasSupportFinancialAndOperationalDimensions() {
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "财务", "财务状况（3分）", new BigDecimal("3"))).isEqualTo("财务状况");
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "财务状况", "财务状况（3分）", new BigDecimal("3"))).isEqualTo("财务状况");
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "运营", "电子商城运营时间（1分）", new BigDecimal("1"))).isEqualTo("电子商城运营时间");
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "运营经验", "电子商城运营时间（1分）", new BigDecimal("1"))).isEqualTo("电子商城运营时间");
        assertThat(OriginalScoreEvidencePolicy.publishedDimension(
                "财务", "授信额度（1分）", new BigDecimal("1"))).isEqualTo("授信额度");

        assertThat(OriginalScoreEvidencePolicy.matchesDimensionAlias("电子商城运营时间（1分）", "运营")).isTrue();
        assertThat(OriginalScoreEvidencePolicy.matchesDimensionAlias("电子商城运营时间（1分）", "运营经验")).isTrue();
        assertThat(OriginalScoreEvidencePolicy.matchesDimensionAlias("授信额度（1分）", "财务")).isTrue();
        assertThat(OriginalScoreEvidencePolicy.matchesDimensionAlias("授信额度（1分）", "财务状况")).isTrue();
        assertThat(OriginalScoreEvidencePolicy.matchesDimensionAlias("电子商城运营时间（1分）", "技术方案")).isFalse();
    }

    @Test void mutuallyExclusiveGradeTiersUseTheHighestFivePointScore() {
        String tiers = "按优良中差分档评定：5分、4分、3分或2分。";
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-tiers", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, "本项目评分办法", 2, 3, List.of(), List.of(
                        cell("total", 0, 0, "本项目评分总分5分"), cell("name", 1, 0, "档位评分"),
                        cell("rule", 1, 1, tiers), cell("weight", 1, 2, "5分")), true)), List.of());

        var correct = OriginalScoreEvidencePolicy.verifyModel(source, "", List.of(
                fact("1", "档位评分", "5", tiers, "rule")), new BigDecimal("5"));
        var wrong = OriginalScoreEvidencePolicy.verifyModel(source, "", List.of(
                fact("1", "档位评分", "4", tiers, "rule")), new BigDecimal("4"));

        assertThat(correct.publishable()).isTrue();
        assertThat(wrong.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_WEIGHT_SOURCE_MISMATCH");
        assertThat(wrong.problems()).anySatisfy(problem -> {
            if ("ITEM_WEIGHT_SOURCE_MISMATCH".equals(problem.code())) {
                assertThat(problem.expected()).isEqualTo(new BigDecimal("5"));
                assertThat(problem.actual()).isEqualTo(new BigDecimal("4"));
            }
        });
    }

    @Test void qualificationSeparatesInSchemeOutOfSchemeAndUnverifiedSourceFacts() {
        var scoring = new DocumentArtifact.CanonicalTable("t-score", 1, "本项目评分办法",
                2, 3, List.of(), List.of(cell("total", 0, 0, "本项目评分总分100分"),
                cell("name", 1, 0, "技术方案"), cell("rule", 1, 1, "技术方案最高得100分"),
                cell("weight", 1, 2, "100分")), true);
        var contract = new DocumentArtifact.CanonicalTable("t-contract", 2, "合同履约考核",
                1, 2, List.of(), List.of(cell("contract-rule", 0, 0, "合同履约考核最高得40分")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-scope", "pdf", "2.0"),
                List.of(scoring, contract), List.of());
        var inScheme = new MultimodalScoreFact("本项目评分办法", "1", "", "技术方案",
                new BigDecimal("100"), "技术方案最高得100分", "OBJECTIVE", 3,
                "t-score", List.of("rule"), "技术方案最高得100分", 0.9);
        var outOfScheme = new MultimodalScoreFact("本项目评分办法", "2", "", "合同履约考核",
                new BigDecimal("40"), "合同履约考核最高得40分", "OBJECTIVE", 3,
                "t-contract", List.of("contract-rule"), "合同履约考核最高得40分", 0.9);
        var unverified = new MultimodalScoreFact("本项目评分办法", "3", "", "未定位条款",
                BigDecimal.ONE, "来源未定位", "OBJECTIVE", 3,
                "missing-table", List.of("missing-cell"), "来源未定位", 0.9);

        var report = OriginalScoreEvidencePolicy.verifyModel(artifact, "",
                List.of(inScheme, outOfScheme, unverified), new BigDecimal("100"));

        assertThat(report.qualifications()).extracting(OriginalScoreEvidencePolicy.Qualification::scopeStatus)
                .containsExactly("IN_SCHEME", "OUT_OF_SCHEME", "SOURCE_UNVERIFIED");
        assertThat(report.qualifications()).extracting(OriginalScoreEvidencePolicy.Qualification::candidateEligible)
                .containsExactly(true, false, false);
        assertThat(report.qualifications().get(0).schemeId()).startsWith("scheme-");
        assertThat(report.qualifications().get(1).reason()).isEqualTo("ITEM_SCHEME_SCOPE_MISMATCH");
        assertThat(report.qualifications().get(2).reason()).isEqualTo("SOURCE_LOCATOR_NOT_VERIFIED");
    }

    @Test void unmarkedTableWithBlankSectionCannotJoinOnlyScoringScheme() {
        var scoring = new DocumentArtifact.CanonicalTable("t-score", 1, "本项目评分办法",
                2, 3, List.of(), List.of(cell("total", 0, 0, "本项目评分总分100分"),
                cell("name", 1, 0, "技术方案"), cell("rule", 1, 1, "技术方案最高得100分"),
                cell("weight", 1, 2, "100分")), true);
        var unmarked = new DocumentArtifact.CanonicalTable("t-unmarked", 2, "",
                2, 1, List.of(), List.of(cell("unmarked-total", 0, 0, "本项目评分总分200分"),
                cell("unmarked-rule", 1, 0, "服务评价最高得20分")), false);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-unmarked", "pdf", "2.0"),
                List.of(scoring, unmarked), List.of());
        var fact = new MultimodalScoreFact("本项目评分办法", "1", "", "服务评价",
                new BigDecimal("20"), "服务评价最高得20分", "OBJECTIVE", 2,
                "t-unmarked", List.of("unmarked-rule"), "服务评价最高得20分", 0.9);

        var report = OriginalScoreEvidencePolicy.verifyModel(artifact,
                "## 本项目评分办法\n总分100分。", List.of(fact), new BigDecimal("100"));

        assertThat(report.total().value()).isEqualByComparingTo("100");
        assertThat(report.total().source()).isEqualTo("TABLE");
        assertThat(report.total().locator()).isEqualTo("t-score:total");
        assertThat(report.qualifications()).singleElement().satisfies(qualification -> {
            assertThat(qualification.scopeStatus()).isEqualTo("SOURCE_UNVERIFIED");
            assertThat(qualification.reason()).isEqualTo("SOURCE_SCHEME_NOT_VERIFIED");
            assertThat(qualification.candidateEligible()).isFalse();
        });
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("SOURCE_SCHEME_NOT_VERIFIED");
    }

    @Test void markedScoringTableWithBlankSectionCannotBeInferredFromTextTotalAlone() {
        var unlocated = new DocumentArtifact.CanonicalTable("t-unlocated", 2, "",
                1, 2, List.of(), List.of(cell("rule", 0, 0, "技术方案最高得100分"),
                cell("weight", 0, 1, "100分")), true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.docx", "sha-unlocated", "docx", "2.0"),
                List.of(unlocated), List.of());
        var fact = new MultimodalScoreFact("", "1", "", "技术方案", new BigDecimal("100"),
                "技术方案最高得100分", "OBJECTIVE", 2, "t-unlocated", List.of("rule"),
                "技术方案最高得100分", 0.9);

        var report = OriginalScoreEvidencePolicy.verifyModel(artifact,
                "**第三章 评审标准**\n本项目总分为 **100** 分。", List.of(fact), new BigDecimal("100"));

        assertThat(report.qualifications()).singleElement().satisfies(qualification -> {
            assertThat(qualification.scopeStatus()).isEqualTo("SOURCE_UNVERIFIED");
            assertThat(qualification.candidateEligible()).isFalse();
            assertThat(qualification.reason()).isEqualTo("SOURCE_SCHEME_NOT_VERIFIED");
        });
        assertThat(OriginalScoringContext.schemes(artifact,
                "**第三章 评审标准**\n本项目总分为 **100** 分.")).singleElement().satisfies(scheme ->
                assertThat(scheme.tableIds()).doesNotContain("t-unlocated"));
    }

    @Test void crossChapterContractFactsStayAuditableOnlyAfterTheirTextLocatorIsVerified() {
        String sourceText = "# 第三章 评审办法\n本项目总分100分。\n技术方案得100分。"
                + "\n# 第四章 合同条款\n回复率须达到100%。";
        String schemeId = OriginalScoringContext.schemeId("第三章 评审办法");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-chapters", "pdf", "2.0"),
                List.of(), List.of());
        var outOfScheme = new MultimodalScoreFact("合同条款", "A1", "", "合同履约响应率",
                new BigDecimal("100"), "回复率须达到100%", "OBJECTIVE", null, "",
                List.of("text:line[4]"), "回复率须达到100%", 0.99, ChildAggregation.SUM,
                schemeId, "OUT_OF_SCHEME");
        var invalidAuditQuote = new MultimodalScoreFact("合同条款", "A2", "", "未核实条款",
                new BigDecimal("100"), "招标文件其他地方写了100分", "OBJECTIVE", null, "",
                List.of("text:line[4]"), "招标文件其他地方写了100分", 0.99, ChildAggregation.SUM,
                schemeId, "OUT_OF_SCHEME");

        var report = OriginalScoreEvidencePolicy.verifyModel(artifact, sourceText,
                List.of(outOfScheme, invalidAuditQuote), new BigDecimal("100"));

        assertThat(report.qualifications()).extracting(OriginalScoreEvidencePolicy.Qualification::scopeStatus)
                .containsExactly("OUT_OF_SCHEME", "SOURCE_UNVERIFIED");
        assertThat(report.qualifications()).extracting(OriginalScoreEvidencePolicy.Qualification::candidateEligible)
                .containsExactly(false, false);
        assertThat(report.qualifications().get(0).locator()).isEqualTo("text:line[4]");
        assertThat(report.qualifications().get(1).reason()).isEqualTo("SOURCE_LOCATOR_NOT_VERIFIED");
    }

    @Test void verifiesTextLocatorWhenMarkItDownWrapsTheWeightInBold() {
        String sourceText = "# 第三章 评审标准\n本项目总分为 **100** 分。\n（一）商务部分（响应报价）**30** 分";
        String schemeId = OriginalScoringContext.schemeId("第三章 评审标准");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.docx", "sha-bold", "docx", "2.0"),
                List.of(), List.of());
        var fact = new MultimodalScoreFact("第三章 评审标准", "F1", "", "响应报价",
                new BigDecimal("30"), "报价评审", "OBJECTIVE", null, "",
                List.of("text:line[2]"), "（一）商务部分（响应报价）30 分", 0.99,
                ChildAggregation.SUM, schemeId, "IN_SCHEME");

        var report = OriginalScoreEvidencePolicy.verifyModel(artifact, sourceText, List.of(fact), new BigDecimal("100"));

        assertThat(report.qualifications()).singleElement().satisfies(qualification -> {
            assertThat(qualification.candidateEligible()).isTrue();
            assertThat(qualification.locator()).isEqualTo("text:line[2]");
        });
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
