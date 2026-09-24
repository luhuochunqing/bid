package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class MultimodalScoreFactValidatorTest {
    private final MultimodalScoreFactValidator validator = new MultimodalScoreFactValidator();

    @Test void approvesCompleteVerbatimScoringSet() {
        var result = validator.validate(List.of(
                sourceFact("1", "10", "c1-rule"), sourceFact("2", "90", "c2-rule")),
                sourceBoundArtifact(), new BigDecimal("100"));
        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test void acceptsParagraphBackedFactWithTextLocator() {
        String text = "# 评分办法\n本项目总分为 30 分。商务部分（响应报价）30 分";
        var fact = new MultimodalScoreFact("商务部分", "响应报价", "", "商务部分（响应报价）",
                new BigDecimal("30"), "商务部分（响应报价）30 分", "OBJECTIVE", null, "", List.of("text:line[1]"),
                "商务部分（响应报价）30 分", 0.9, ChildAggregation.SUM);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "docx", "2.0"),
                List.of(), List.of());
        var result = validator.validate(List.of(fact), artifact,
                OriginalScoringContext.resolve(artifact, text), text, new BigDecimal("30"));
        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test void acceptsParagraphQuoteSplitAcrossDocxRuns() {
        String text = "# 评分办法\n本项目总分\n为 10 分。业绩：满足条件得\n10\u00a0分";
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩",
                new BigDecimal("10"), "满足条件得10分", "OBJECTIVE", null, "",
                List.of("text:line[2]"), "满足条件得 10 分", 0.9, ChildAggregation.SUM);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "docx", "2.0"),
                List.of(), List.of());

        var result = validator.validate(List.of(fact), artifact,
                OriginalScoringContext.resolve(artifact, text), text, new BigDecimal("10"));

        assertThat(result.autoApprovable()).isTrue();
        assertThat(result.violations()).isEmpty();
    }

    @Test void rejectsWhitespaceOnlyTextQuote() {
        String text = "本项目总分为 10 分";
        var fact = new MultimodalScoreFact("评分办法", "1", "", "业绩",
                new BigDecimal("10"), "满足条件得10分", "OBJECTIVE", null, "",
                List.of("text:line[0]"), "\u00a0", 0.9, ChildAggregation.SUM);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "docx", "2.0"),
                List.of(), List.of());

        var result = validator.validate(List.of(fact), artifact,
                OriginalScoringContext.resolve(artifact, text), text, new BigDecimal("10"));

        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("SOURCE_LOCATOR_INVALID", "QUOTE_NOT_VERBATIM");
    }

    @Test void reportsAllHardDifferencesWithoutDroppingCandidates() {
        var result = validator.validate(List.of(
                fact("商务", "2", null, "报价", "30", "不存在的改写", "c1"),
                fact("商务", "2", null, "重复", "20", "原文二", "c2")),
                artifact(cell("c1", "原文一"), cell("c2", "原文二")), new BigDecimal("100"));
        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.facts()).hasSize(2);
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("SEQUENCE_GAP", "DUPLICATE_CODE", "WEIGHT_SUM_MISMATCH", "QUOTE_NOT_VERBATIM");
    }

    @Test void validatesParentWeightButCountsOnlyLeaves() {
        var result = validator.validate(List.of(
                fact("技术", "1", null, "方案", "40", "方案满分40分", "p"),
                fact("技术", "1.1", "1", "实施", "10", "实施得10分", "c1"),
                fact("技术", "1.2", "1", "服务", "20", "服务得20分", "c2")),
                artifact(cell("p", "方案满分40分"), cell("c1", "实施得10分"), cell("c2", "服务得20分"),
                        cell("total", "本项目评分总分30分")), new BigDecimal("30"));
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("PARENT_WEIGHT_MISMATCH");
        assertThat(result.leafTotal()).isEqualByComparingTo("30");
    }

    @Test void validatesParentChildAcrossPhysicalTablesInsideOneScheme() {
        String schemeId = OriginalScoringContext.schemeId("本项目评分办法");
        var parentTable = new DocumentArtifact.CanonicalTable("t-parent", 1, "本项目评分办法 / 商务汇总",
                2, 2, List.of(), List.of(cellAt("p-name", 0, "商务部分"),
                cellAt("p-rule", 1, "商务部分由以下子项累计计分，最高10分")), true);
        var childTable = new DocumentArtifact.CanonicalTable("t-child", 2, "本项目评分办法 / 商务明细",
                2, 2, List.of(), List.of(cellAt("c-name", 0, "履约能力"),
                cellAt("c-rule", 1, "履约能力按原文要求得10分")), true);
        var summaryTable = new DocumentArtifact.CanonicalTable("t-total", 1, "本项目评分办法 / 汇总",
                1, 1, List.of(), List.of(cellAt("total", 0, "本项目评分总分10分")), false);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha-cross-table", "pdf", "2.0"),
                List.of(parentTable, childTable, summaryTable), List.of());
        var parent = new MultimodalScoreFact("评审标准", "1", "", "商务部分", BigDecimal.TEN,
                "商务部分由以下子项累计计分，最高10分", "OBJECTIVE", 1, "t-parent", List.of("p-rule"),
                "商务部分由以下子项累计计分，最高10分", 0.9, ChildAggregation.SUM, schemeId, "IN_SCHEME");
        var child = new MultimodalScoreFact("商务评分表", "1.1", "1", "履约能力", BigDecimal.TEN,
                "履约能力按原文要求得10分", "OBJECTIVE", 2, "t-child", List.of("c-rule"),
                "履约能力按原文要求得10分", 0.9, ChildAggregation.SUM, schemeId, "IN_SCHEME");

        var result = validator.validate(List.of(parent, child), artifact,
                OriginalScoringContext.resolve(artifact, "# 本项目评分办法\n总分10分。"),
                "# 本项目评分办法\n总分10分。", BigDecimal.TEN);

        assertThat(result.leafTotal()).isEqualByComparingTo("10");
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .doesNotContain("PARENT_NOT_FOUND", "PARENT_WEIGHT_MISMATCH", "WEIGHT_SUM_MISMATCH");
    }

    @Test void sharedCapCountsParentOnceAndDoesNotRequireChildrenToSumToCap() {
        var parent = new MultimodalScoreFact("技术", "1", "", "方案", new BigDecimal("5"),
                "方案条件共享最高得5分", "OBJECTIVE", 3, "t1", List.of("p"),
                "方案条件共享最高得5分", 0.9, ChildAggregation.SHARED_CAP);
        var childA = new MultimodalScoreFact("技术", "1.1", "1", "业绩", new BigDecimal("3"),
                "满足营业额得3分", "OBJECTIVE", 3, "t1", List.of("a"), "满足营业额得3分", 0.9);
        var childB = new MultimodalScoreFact("技术", "1.2", "1", "人员", new BigDecimal("4"),
                "满足人员要求得4分", "OBJECTIVE", 3, "t1", List.of("b"), "满足人员要求得4分", 0.9);
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, "技术", 1, 3, List.of(), List.of(
                        cellAt("p", 1, "方案条件共享最高得5分"), cellAt("a", 1, "满足营业额得3分"),
                        cellAt("b", 1, "满足人员要求得4分"), cellAt("total", 2, "本项目评分总分5分")), true)), List.of());

        var result = validator.validate(List.of(parent, childA, childB), source, new BigDecimal("5"));

        assertThat(result.leafTotal()).isEqualByComparingTo("5");
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .doesNotContain("PARENT_WEIGHT_MISMATCH");
    }

    @Test void permitsMultipleCitedCellsWhenQuoteAndPhysicalPageAreGrounded() {
        var fact = new MultimodalScoreFact("技术", "1", "", "方案", new BigDecimal("5"),
                "方案最高得5分", "OBJECTIVE", 2, "t1", List.of("p1", "p2"), "方案最高得5分", 0.9);
        var cells = List.of(cellAt("p1", 1, "方案最高得5分"),
                new DocumentArtifact.CanonicalTable.CanonicalCellDto("p2", 1, 1, "补充规则", "补充规则",
                        1, 1, false, "pdf:page=2", 2),
                cellAt("total", 2, "本项目评分总分5分"));
        var source = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 1, "技术", 1, 3, List.of(), cells, true)), List.of());

        var result = validator.validate(List.of(fact), source, new BigDecimal("5"));

        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .doesNotContain("SOURCE_LOCATOR_INVALID", "QUOTE_NOT_VERBATIM");
    }

    @Test void unknownOrUnprovenAggregationCannotBeAutoApproved() {
        var source = artifact(cell("p", "方案最高得5分"), cell("c", "实施得5分"),
                cell("total", "本项目评分总分5分"));
        var child = fact("技术", "1.1", "1", "实施", "5", "实施得5分", "c");
        var unknownParent = new MultimodalScoreFact("技术", "1", "", "方案", new BigDecimal("5"),
                "方案最高得5分", "OBJECTIVE", 3, "t1", List.of("p"), "方案最高得5分", 0.9,
                ChildAggregation.UNKNOWN);
        var assertedSharedCap = new MultimodalScoreFact("技术", "1", "", "方案", new BigDecimal("5"),
                "方案最高得5分", "OBJECTIVE", 3, "t1", List.of("p"), "方案最高得5分", 0.9,
                ChildAggregation.SHARED_CAP);

        var unknown = validator.validate(List.of(unknownParent, child), source, new BigDecimal("5"));
        var unsupported = validator.validate(List.of(assertedSharedCap, child), source, new BigDecimal("5"));

        assertThat(unknown.autoApprovable()).isFalse();
        assertThat(unknown.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("CHILD_AGGREGATION_UNKNOWN");
        assertThat(unsupported.autoApprovable()).isFalse();
        assertThat(unsupported.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE");
    }

    @Test void rejectsEmptyFactsEvenWhenDeclaredTotalIsZero() {
        var result = validator.validate(List.of(), artifact(), BigDecimal.ZERO);
        assertThat(result.autoApprovable()).isFalse();
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("EMPTY_FACTS");
    }

    @Test void rejectsMissingRuleDimensionAndInvalidScoreType() {
        var invalid = new MultimodalScoreFact("商务", "1", "", "", BigDecimal.TEN, "", "MIXED",
                3, "t1", List.of("c1"), "原文", 0.9);
        var result = validator.validate(List.of(invalid), artifact(cell("c1", "原文")), BigDecimal.TEN);
        assertThat(result.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("DIMENSION_MISSING", "RULE_MISSING", "SCORE_TYPE_INVALID");
    }

    @Test void rejectsModelTotalShrunkToMatchMissingItem() {
        var report = validator.validate(List.of(sourceFact("1", "10", "c1-rule")),
                sourceBoundArtifact(), new BigDecimal("10"));

        assertThat(report.autoApprovable()).isFalse();
        assertThat(report.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("MODEL_TOTAL_SOURCE_MISMATCH");
        assertThat(report.violations()).anySatisfy(violation -> {
            if ("MODEL_TOTAL_SOURCE_MISMATCH".equals(violation.code())) {
                assertThat(violation.expected()).isEqualTo(new BigDecimal("100"));
                assertThat(violation.actual()).isEqualTo(new BigDecimal("10"));
            }
        });
    }

    @Test void rejectsOneItemInflatedToMatchOriginalTotal() {
        var report = validator.validate(List.of(sourceFact("1", "100", "c1-rule")),
                sourceBoundArtifact(), new BigDecimal("100"));

        assertThat(report.autoApprovable()).isFalse();
        assertThat(report.violations()).extracting(MultimodalScoreFactValidator.Violation::code)
                .contains("ITEM_WEIGHT_SOURCE_MISMATCH");
    }

    @Test void rejectsCompensatingItemErrorsEvenWhenLeafSumIsCorrect() {
        var report = validator.validate(List.of(sourceFact("1", "20", "c1-rule"),
                sourceFact("2", "80", "c2-rule")), sourceBoundArtifact(), new BigDecimal("100"));

        assertThat(report.leafTotal()).isEqualByComparingTo("100");
        assertThat(report.autoApprovable()).isFalse();
        assertThat(report.violations()).filteredOn(v -> "ITEM_WEIGHT_SOURCE_MISMATCH".equals(v.code()))
                .hasSize(2);
    }

    private static MultimodalScoreFact sourceFact(String code, String weight, String ruleCellId) {
        String rule = "1".equals(code) ? "销售额达到1亿元得10分" : "技术方案按完整性与可行性综合评议，最高得90分";
        String name = "1".equals(code) ? "销售额" : "技术方案";
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, "score-t1", List.of(ruleCellId), rule, 0.9);
    }

    private static DocumentArtifact sourceBoundArtifact() {
        var cells = List.of(
                sourceCell("total", 0, 0, "本项目评分总分：100分"),
                sourceCell("c1-name", 1, 0, "销售额"),
                sourceCell("c1-rule", 1, 1, "销售额达到1亿元得10分"),
                sourceCell("c1-weight", 1, 2, "10分"),
                sourceCell("c2-name", 2, 0, "技术方案"),
                sourceCell("c2-rule", 2, 1, "技术方案按完整性与可行性综合评议，最高得90分"),
                sourceCell("c2-weight", 2, 2, "90分"));
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("score-t1", 3, "本项目评分办法", 3, 3,
                        List.of(), cells, true)), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto sourceCell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column,
                text, text, 1, 1, false, "");
    }

    private static MultimodalScoreFact fact(String section, String code, String parent, String name,
                                             String weight, String quote, String cellId) {
        return new MultimodalScoreFact(section, code, parent, name, new BigDecimal(weight), quote,
                "OBJECTIVE", 3, "t1", List.of(cellId), quote, 0.9);
    }
    private static DocumentArtifact artifact(DocumentArtifact.CanonicalTable.CanonicalCellDto... cells) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("d", "sha", "docx", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, "商务", 3, 2, List.of(), List.of(cells), true)), List.of());
    }
    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, 1, 1, text, text, 1, 1, false, "");
    }
    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cellAt(String id, int row, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, 1, text, text, 1, 1, false, "");
    }
}
