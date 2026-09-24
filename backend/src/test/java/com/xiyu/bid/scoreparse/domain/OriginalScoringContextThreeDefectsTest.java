package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoringContextThreeDefectsTest {
    private static final String SCHEME = "本项目评分办法";
    private static final String SALES_RULE = "满分10分，销售额达到1亿元得10分。";
    private static final String TECH_RULE = "技术方案按完整性与可行性评议，最高得90分。";

    @Test void itemRuleStartingWithMaximumCannotSupplyMissingSchemeTotal() {
        DocumentArtifact original = itemMaximumDocument(false);
        var result = new MultimodalScoreFactValidator().validate(List.of(salesFact()), original, "", bd("10"));
        assertThat(result.originalTotal()).isNull();
        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("ORIGINAL_TOTAL_UNKNOWN");
    }

    @Test void sparseDetailRuleCellCannotSupplySchemeTotal() {
        var full = itemMaximumDocument(false);
        var detail = full.tables().get(0);
        var cells = detail.cells().stream().filter(c -> c.row() != 1 || c.column() == 2).toList();
        var sparse = new DocumentArtifact.CanonicalTable(detail.tableId(), detail.sourcePage(),
                detail.sourceSection(), detail.rows(), detail.columns(), detail.mergedRegions(), cells,
                detail.isScoreTable());
        var original = new DocumentArtifact(full.artifact(), List.of(sparse), List.of());
        var context = OriginalScoringContext.resolve(original, "");
        assertThat(context.total().value()).isNull();
        assertThat(context.total().problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_TOTAL_UNKNOWN");
    }

    @Test void itemRuleStartingWithMaximumDoesNotConflictWithBodyHundred() {
        DocumentArtifact original = itemMaximumDocument(false);
        var result = new MultimodalScoreFactValidator().validate(List.of(salesFact(), technicalFact()),
                original, "本项目评分总分100分。", bd("100"));
        assertThat(result.originalTotal()).isEqualByComparingTo("100");
        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .doesNotContain("ORIGINAL_TOTAL_CONFLICT");
    }

    @Test void independentSummaryMaximumStillSuppliesHundredWithoutBecomingAnItem() {
        DocumentArtifact original = itemMaximumDocument(true);
        var result = new MultimodalScoreFactValidator().validate(List.of(salesFact(), technicalFact()),
                original, "", bd("100"));
        assertThat(result.originalTotal()).isEqualByComparingTo("100");
        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.facts()).hasSize(2);
    }

    @Test void modelMustRejectConflictingLocalSubtotalsWhileRetainingWholeHundred() {
        DocumentArtifact original = twoCategoryTables("30", "70");
        var result = new MultimodalScoreFactValidator().validate(categoryFacts(), original,
                "# 本项目评分办法\n总分100分。", bd("100"));
        assertThat(result.originalTotal()).isEqualByComparingTo("100");
        assertThat(result.leafTotal()).isEqualByComparingTo("100");
        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.violations()).filteredOn(v -> v.code().equals("TABLE_SUBTOTAL_MISMATCH"))
                .hasSize(2).extracting(MultimodalScoreFactValidator.Violation::evidence)
                .contains("t-business:t-business-r2-c3", "t-technical:t-technical-r2-c3");
    }

    @Test void modelAllowsMatchingLocalSubtotals() {
        var result = new MultimodalScoreFactValidator().validate(categoryFacts(),
                twoCategoryTables("20", "80"), "# 本项目评分办法\n总分100分。", bd("100"));
        assertThat(result.autoApprovable()).isTrue();
    }

    @Test void siblingScoringSubheadingsBelongToOneParentScheme() {
        DocumentArtifact original = twoCategoryTables("20", "80", "商务评分标准", "技术评分标准");
        String text = "# 本项目评分办法\n总分100分。\n## 商务评分标准\n商务20分明细表。"
                + "\n## 技术评分标准\n技术80分明细表。";
        var result = new MultimodalScoreFactValidator().validate(categoryFacts(), original, text, bd("100"));
        assertThat(result.originalTotal()).isEqualByComparingTo("100");
        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .doesNotContain("ORIGINAL_SCHEME_AMBIGUOUS");
    }

    @Test void separatePackagesRemainAmbiguousEvenWhenBothTotalsEqual() {
        DocumentArtifact original = new DocumentArtifact(meta(), List.of(
                table("t-first", "第一标包评分办法", "实施方案", "20", "20"),
                table("t-second", "第二标包评分办法", "技术方案", "80", "80")), List.of());
        String text = "# 第一标包评分办法\n总分100分。\n# 第二标包评分办法\n总分100分。";
        var context = OriginalScoringContext.resolve(original, text);
        assertThat(context.total().value()).isNull();
        assertThat(context.total().problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_SCHEME_AMBIGUOUS");
    }

    @Test void adjacentMarkdownHashesDoNotHideIndependentPackageHeading() {
        DocumentArtifact original = new DocumentArtifact(meta(), List.of(
                table("t-first", "第一标包评分办法", "实施方案", "20", "20")), List.of());
        var context = OriginalScoringContext.resolve(original,
                "#第一标包评分办法\n总分100分。\n#第二标包评分办法\n总分80分。");
        assertThat(context.total().value()).isNull();
        assertThat(context.total().problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_SCHEME_AMBIGUOUS");
    }

    @Test void newPeerSchemeStopsPreviousHeadingInheritance() {
        DocumentArtifact original = twoCategoryTables("20", "80", "商务评分标准", "技术评分标准");
        String text = "# 本项目评分办法\n总分100分。\n## 商务评分标准\n商务20分明细表。"
                + "\n# 第二标包评分办法\n总分80分。";
        var context = OriginalScoringContext.resolve(original, text);
        assertThat(context.total().value()).isNull();
        assertThat(context.total().problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_SCHEME_AMBIGUOUS");
    }

    @Test void repeatedEvaluationStandardHeadingContinuesTheSameLogicalScheme() {
        var source = new DocumentArtifact(meta(), List.of(), List.of());
        String text = "# 第三章 评审标准\n# 评审标准\n本项目总分为100分。";

        var context = OriginalScoringContext.resolve(source, text);
        var schemes = OriginalScoringContext.schemes(source, text);

        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(schemes).hasSize(1);
        assertThat(schemes.get(0).title()).isEqualTo("第三章 评审标准");
    }

    @Test void contractPerformanceChapterIsNotOwnedByTheScoringScheme() {
        DocumentArtifact original = new DocumentArtifact(meta(), List.of(
                table("t-main", SCHEME + " / 商务", "企业业绩", "20", "20"),
                table("t-contract", "合同履约考核评分", "履约考核", "40", "40")), List.of());
        var context = OriginalScoringContext.resolve(original, "# 本项目评分办法\n总分100分。\n# 合同履约考核评分\n履约考核40分。");

        assertThat(context.owns(original.tables().get(0))).isTrue();
        assertThat(context.owns(original.tables().get(1))).isFalse();
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(OriginalScoringContext.schemes(original,
                "# 本项目评分办法\n总分100分。\n# 合同履约考核评分\n履约考核40分。"))
                .hasSize(1).first().extracting(OriginalScoringContext.SchemeEvidence::title)
                .isEqualTo(SCHEME);
    }

    @Test void sourceTotalCarriesExactOriginalLineAndLocation() {
        var context = OriginalScoringContext.resolve(itemMaximumDocument(false),
                "本项目评分总分100分。\n# 本项目评分办法\n技术方案细则。");

        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().locator()).isEqualTo("text:line[0]");
        assertThat(context.total().quote()).isEqualTo("本项目评分总分100分。");
    }

    @Test void sourceScoreDistributionTableCellCanDeclareTheWholeSchemeTotal() {
        var table = new DocumentArtifact.CanonicalTable("t-score-summary", 28, "评审办法前附表",
                1, 1, List.of(), List.of(cell("total-cell", 0, 0, "分值构成（总分 100 分）")), true);
        var source = new DocumentArtifact(meta(), List.of(table), List.of());

        var context = OriginalScoringContext.resolve(source, "# 第三章 评审办法\n评分细则见后续表格。");

        assertThat(context.total().value()).as("scheme=%s owns=%s total=%s", context.scheme(),
                context.owns(table), context.total()).isEqualByComparingTo("100");
        assertThat(context.total().locator()).isEqualTo("t-score-summary:total-cell");
        assertThat(context.total().quote()).isEqualTo("分值构成（总分 100 分）");
    }

    @Test void textScopesKeepContractPerformanceChapterOutOfScoringInput() {
        var scopes = OriginalScoringContext.textScopes("# 本项目评分办法\n总分100分。\n## 商务评分标准\n人员业绩得20分。"
                + "\n# 合同履约考核\n服务期每月得5分。");

        assertThat(scopes).extracting(OriginalScoringContext.TextScope::scopeStatus)
                .containsExactly("IN_SCHEME", "OUT_OF_SCHEME");
        assertThat(scopes.get(0).text()).contains("人员业绩得20分").doesNotContain("服务期每月");
        assertThat(scopes.get(1).text()).contains("服务期每月");
    }

    @Test void sameLogicalSchemeAcrossTwoPhysicalTablesKeepsOneVerifiedHundredPointManifest() {
        var source = twoCategoryTables("20", "80");
        String text = "# 本项目评分办法\n本项目总分为100分。\n## 商务评分标准\n履约能力20分。"
                + "\n## 技术评分标准\n实施方案80分。";

        var schemes = OriginalScoringContext.schemes(source, text);

        assertThat(schemes).hasSize(1);
        var scheme = schemes.get(0);
        assertThat(scheme.layoutStatus()).isEqualTo("MULTI_TABLE_SAME_SCHEME");
        assertThat(scheme.tableIds()).containsExactlyInAnyOrder("t-business", "t-technical");
        assertThat(scheme.totalEvidenceStatus()).isEqualTo("VERIFIED");
        assertThat(scheme.declaredTotal()).isEqualByComparingTo("100");
        assertThat(scheme.totalLocator()).isEqualTo("text:line[1]");
        assertThat(scheme.totalQuote()).isEqualTo("本项目总分为100分。");
    }

    private static DocumentArtifact itemMaximumDocument(boolean summary) {
        List<DocumentArtifact.CanonicalTable> tables = new ArrayList<>();
        tables.add(new DocumentArtifact.CanonicalTable("t-items", 1, SCHEME + " / 评分明细", 3, 4,
                List.of(), List.of(
                cell("i-h0", 0, 0, "序号"), cell("i-h1", 0, 1, "评分因素"),
                cell("i-h2", 0, 2, "评分规则"), cell("i-h3", 0, 3, "分值"),
                cell("i-code1", 1, 0, "1"), cell("i-name1", 1, 1, "销售额"),
                cell("i-rule1", 1, 2, SALES_RULE), cell("i-weight1", 1, 3, "10"),
                cell("i-code2", 2, 0, "2"), cell("i-name2", 2, 1, "技术方案"),
                cell("i-rule2", 2, 2, TECH_RULE), cell("i-weight2", 2, 3, "90")), true));
        if (summary) tables.add(new DocumentArtifact.CanonicalTable("t-summary", 1,
                SCHEME + " / 方案汇总表", 2, 2, List.of(), List.of(
                cell("s-h0", 0, 0, "汇总项"), cell("s-h1", 0, 1, "分值"),
                cell("s-total", 1, 0, "合计"), cell("s-value", 1, 1, "满分100分")), false));
        return new DocumentArtifact(meta(), tables, List.of());
    }

    private static MultimodalScoreFact salesFact() {
        return fact("t-items", "1", "销售额", "10", "i-rule1", SALES_RULE);
    }

    private static MultimodalScoreFact technicalFact() {
        return fact("t-items", "2", "技术方案", "90", "i-rule2", TECH_RULE);
    }

    private static List<MultimodalScoreFact> categoryFacts() {
        return List.of(fact("t-business", "1", "履约能力", "20", "t-business-r1-c2",
                        "履约能力按原文要求评议，最高得20分。"),
                fact("t-technical", "2", "实施方案", "80", "t-technical-r1-c2",
                        "实施方案按原文要求评议，最高得80分。"));
    }

    private static MultimodalScoreFact fact(String table, String code, String name, String weight,
                                            String cellId, String rule) {
        return new MultimodalScoreFact(SCHEME, code, "", name, bd(weight), rule,
                "OBJECTIVE", 1, table, List.of(cellId), rule, 0.9);
    }

    private static DocumentArtifact twoCategoryTables(String businessSubtotal, String technicalSubtotal) {
        return twoCategoryTables(businessSubtotal, technicalSubtotal, "商务", "技术");
    }

    private static DocumentArtifact twoCategoryTables(String businessSubtotal, String technicalSubtotal,
                                                      String businessSection, String technicalSection) {
        return new DocumentArtifact(meta(), List.of(
                table("t-business", SCHEME + " / " + businessSection, "履约能力", "20", businessSubtotal),
                table("t-technical", SCHEME + " / " + technicalSection, "实施方案", "80", technicalSubtotal)),
                List.of());
    }

    private static DocumentArtifact.CanonicalTable table(String id, String section, String name,
                                                         String weight, String subtotal) {
        List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells = new ArrayList<>();
        List<List<String>> rows = List.of(List.of("序号", "评分因素", "评分规则", "分值"),
                List.of("1", name, name + "按原文要求评议，最高得" + weight + "分。", weight),
                List.of("合计", "", "", subtotal));
        for (int row = 0; row < rows.size(); row++) for (int col = 0; col < 4; col++) {
            cells.add(cell(id + "-r" + row + "-c" + col, row, col, rows.get(row).get(col)));
        }
        return new DocumentArtifact.CanonicalTable(id, 1, section, 3, 4, List.of(), cells, true);
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int col, String value) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, col, value, value,
                1, 1, false, "pdf:page=1;cell=" + id);
    }

    private static DocumentArtifact.ArtifactMeta meta() {
        return new DocumentArtifact.ArtifactMeta("bid.pdf", "sha-shared-original", "pdf", "2.0");
    }

    private static BigDecimal bd(String value) { return new BigDecimal(value); }
}
