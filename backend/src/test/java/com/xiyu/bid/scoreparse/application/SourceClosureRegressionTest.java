// Input: synthetic merged and continued PDF scoring rows.
// Output: source identity, complete-rule and grouped-total regressions.
package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalRuleRestoration;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.SourceFactDeduplication;
import com.xiyu.bid.scoreparse.domain.SourceScoreHierarchyPolicy;
import com.xiyu.bid.scoreparse.domain.SourceTableHeaderPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SourceClosureRegressionTest {
    static final String SECTION = "第四章：评审方法";
    static final String RULE = "提供结算响应承诺书。接受六个月承兑结算得1分；三个月得0.5分；未提供不得分。";

    @Test void explicitMaximumColumnDoesNotRequireStandardRuleHeader() {
        String rule = "每提供一项证书加1分。";
        for (String scoreHeader : List.of("最高分", "最高分（分）", "评分分值")) {
            var table = new CanonicalTable("t", 1, SECTION, 2, 3, List.of(), List.of(
                    cell("h-name", 0, 0, "评审项目", 1), cell("h-rule", 0, 1, "评审内容", 1),
                    cell("h-weight", 0, 2, scoreHeader, 1), cell("name", 1, 0, "认证情况", 1),
                    cell("rule", 1, 1, rule, 1), cell("weight", 1, 2, "10", 1)), true, "PDF");
            var artifact = artifact(table);
            var context = OriginalScoringContext.resolve(artifact, "# " + SECTION);
            var correct = fact("certificate", "", "认证情况", rule, rule, List.of("rule", "weight"), 10);
            assertThat(OriginalScoreEvidencePolicy.verifyModel(context, artifact, List.of(correct), null).problems())
                    .as("explicit %s must override per-certificate award", scoreHeader)
                    .noneMatch(problem -> problem.field().equals("weight"));
            var incorrect = fact("certificate", "", "认证情况", rule, rule, List.of("rule", "weight"), 1);
            assertThat(OriginalScoreEvidencePolicy.verifyModel(context, artifact, List.of(incorrect), null).problems())
                    .anySatisfy(problem -> {
                        assertThat(problem.code()).isEqualTo("ITEM_WEIGHT_SOURCE_MISMATCH");
                        assertThat((BigDecimal) problem.expected()).isEqualByComparingTo("10");
                        assertThat((BigDecimal) problem.actual()).isEqualByComparingTo("1");
                    });
        }
    }

    @Test void aliasesCitingNameAndRuleOrRuleAloneDenoteOneWholeScoringRow() {
        var artifact = artifact(table("t", 1, List.of(
                cell("name", 1, 0, "结算方式（1分）", 1), cell("weight", 1, 1, "1", 1), cell("rule", 1, 2, RULE, 1))));
        var first = fact("settlement", "", "结算方式", "完整结算要求", RULE, List.of("rule"), 1);
        var alias = fact("clone", "", "结算方式", "结算方案", RULE, List.of("name", "rule"), 1);
        var context = OriginalScoringContext.resolve(artifact, "# " + SECTION);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(first, alias), artifact, "");
        var normalized = MultimodalExtractionReviewPolicy.normalized(
                new MultimodalScoreFactExtractor.ExtractionResult(null, bound, "synthetic", 0), context, artifact, "").facts();
        assertThat(normalized).hasSize(1);
        assertThat(OriginalRuleRestoration.restore(normalized.getFirst(), artifact, normalized).text()).contains(RULE);
    }

    @Test void fullQuoteAcrossContinuationRowsBindsEveryOriginalRuleCell() {
        String beginning = "整体配送方案须包含配送网络、车辆人员及保密安全措施。";
        String ending = "根据方案可行性评审：优秀得6分，良好得4分，一般得2分，未提供不得分。";
        var artifact = artifact(table("t", 1, List.of(
                cell("name", 1, 0, "整体配送", 1), cell("weight", 1, 1, "6", 1), cell("begin", 1, 2, beginning, 1),
                cell("blank-name", 2, 0, "", 2), cell("blank-weight", 2, 1, "", 2), cell("end", 2, 2, ending, 2))));
        var input = fact("delivery", "", "技术", beginning + ending, beginning + ending, List.of(), 6);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(input), artifact, "").getFirst();
        assertThat(bound.sourceCellIds()).contains("begin", "end");
        assertThat(OriginalRuleRestoration.restore(bound, artifact, List.of(bound)).text()).contains(beginning, ending);
    }

    @Test void categoryScoresAcrossPhysicalTablesCloseOneSchemeWithoutLiteralTotal() {
        var artifact = artifact(groupTable("commercial", 1, "商务评分（20分）", "20", "商务细则"),
                groupTable("technical", 2, "技术评分（50分）", "50", "技术细则"),
                groupTable("price", 3, "价格评分（30分）", "30", "价格细则"));
        var context = OriginalScoringContext.resolve(artifact, "# " + SECTION);
        assertThat(context.total().value()).isEqualByComparingTo("100");
        var sameSourceBundle = new DocumentArtifact(artifact.artifact(), artifact.tables(), List.of(), List.of(), "2.2",
                List.of(new DocumentArtifact.SourceDocument("commercial", artifact.artifact()),
                        new DocumentArtifact.SourceDocument("technical", artifact.artifact()),
                        new DocumentArtifact.SourceDocument("price", artifact.artifact())));
        assertThat(OriginalScoringContext.resolve(sameSourceBundle, "# " + SECTION).total().value())
                .isEqualByComparingTo("100");
    }

    @Test void groupedTotalCannotBeInferredFromIncompleteCategorySet() {
        var artifact = artifact(groupTable("commercial", 1, "商务评分（20分）", "20", "商务细则"),
                groupTable("technical", 2, "技术评分（50分）", "50", "技术细则"));
        assertThat(OriginalScoringContext.resolve(artifact, "# " + SECTION).total().value()).isNull();
    }

    @Test void headerlessContinuationRetainsTwoLevelColumnRolesAndPunctuationOnlyQuote() {
        String raw = "整体配送方案：须包含配送网络、车辆及人员清单；优秀得6分，良好得4分，一般得2分，未提供不得分。";
        var header = groupTable("head", 1, "商务评分（20分）", "20", "商务细则");
        var continuation = new CanonicalTable("t", 2, SECTION, 1, 4, List.of(), List.of(
                cell("category", 0, 0, "", 2), cell("name", 0, 1, "仓储配送（6分）", 2),
                cell("weight", 0, 2, "6", 3), cell("rule", 0, 3, raw, 3)), true, "PDF");
        var artifact = artifact(header, continuation);
        var input = fact("delivery", "", "技术", "配送要求", raw.replace('：', ':').replace('；', '：'), List.of(), 6);
        var bound = TextPrimaryEvidenceBinder.bind(List.of(input), artifact, "").getFirst();
        assertThat(bound.sourceCellIds()).contains("rule");
        assertThat(bound.pageNumber()).isEqualTo(3);
        assertThat(OriginalRuleRestoration.restore(bound, artifact, List.of(bound)).text()).contains(raw);
    }

    @Test void duplicateEmptyModelRulesCannotEraseUniqueSourceRuleOnHeaderlessPage() {
        var header = groupTable("head", 1, "商务评分（20分）", "20", "商务细则");
        var continuation = new CanonicalTable("t", 2, SECTION, 1, 4, List.of(), List.of(
                cell("category", 0, 0, "", 2), cell("name", 0, 1, "结算方式（1分）", 2),
                cell("weight", 0, 2, "1", 2), cell("rule", 0, 3, RULE, 2)), true, "PDF");
        var artifact = artifact(header, continuation);
        var a = fact("settlement", "", "商务", "", RULE, List.of("rule"), 1);
        var b = fact("clone", "", "商务", "", RULE, List.of("rule"), 1);
        var context = OriginalScoringContext.resolve(artifact, "# " + SECTION);
        var facts = MultimodalExtractionReviewPolicy.normalized(new MultimodalScoreFactExtractor.ExtractionResult(
                null, List.of(a, b), "synthetic", 0), context, artifact, "").facts();
        assertThat(facts).hasSize(1);
        assertThat(OriginalRuleRestoration.restore(facts.getFirst(), artifact, facts).text()).contains(RULE);
    }

    @Test void originalGroupBoundaryOverridesWrongModelParentAfterAliasMerge() {
        var cells = List.of(cell("group-service", 1, 0, "运营服务（6分）", 1),
                cell("s-weight", 1, 1, "4", 1), cell("s-rule", 1, 2, "属地化服务优秀得4分。", 1),
                cell("empty", 2, 0, "", 1), cell("l-weight", 2, 1, "2", 1),
                cell("l-rule", 2, 2, "履约方案优秀得2分。", 1),
                cell("group-tech", 3, 0, "技术能力（5分）", 1), cell("t-weight", 3, 1, "5", 1),
                cell("t-rule", 3, 2, "平台方案优秀得5分。", 1));
        var artifact = artifact(table("t", 1, cells));
        var service = fact("service", "", "运营服务", "", "运营服务（6分）", List.of("group-service"), 6);
        var tech = fact("tech", "", "技术能力", "", "技术能力（5分）", List.of("group-tech"), 5);
        var child = fact("local", "tech", "属地化服务", "属地化服务优秀得4分。", "属地化服务优秀得4分。", List.of("s-rule"), 4);
        var context = OriginalScoringContext.resolve(artifact, "# " + SECTION);
        var facts = MultimodalExtractionReviewPolicy.normalized(new MultimodalScoreFactExtractor.ExtractionResult(
                null, List.of(service, tech, child), "synthetic", 0), context, artifact, "").facts();
        assertThat(facts.stream().filter(f -> f.code().equals("local")).findFirst().orElseThrow().parentCode()).isEqualTo("service");
    }

    @Test void outerGroupSwitchEndsPreviousInnerGroupScope() {
        var cells = new java.util.ArrayList<>(groupTable("t", 1, "商务评分（20分）", "6", "运营细则").cells());
        cells.removeIf(c -> c.row() == 1 && c.column() == 1);
        cells.add(cell("operations-label", 1, 1, "运营服务（6分）", 1));
        cells.addAll(List.of(cell("technical-label", 2, 0, "技术评分（50分）", 1),
                cell("technical-name", 2, 1, "技术细则", 1), cell("technical-weight", 2, 2, "4", 1),
                cell("technical-rule", 2, 3, "技术细则优秀得4分。", 1)));
        var table = new CanonicalTable("t", 1, SECTION, 3, 4, List.of(), cells, true, "PDF");
        var commercial = fact("commercial", "", "商务", "", "商务评分（20分）", List.of("tg"), 20);
        var operations = fact("operations", "commercial", "运营服务", "", "运营服务（6分）", List.of("operations-label"), 6);
        var technical = fact("technical", "", "技术", "", "技术评分（50分）", List.of("technical-label"), 50);
        var commercialChild = fact("service", "operations", "运营细则", "", "运营细则", List.of("tr"), 6);
        var technicalChild = fact("platform", "technical", "技术细则", "", "技术细则优秀得4分。", List.of("technical-rule"), 4);
        var restored = SourceScoreHierarchyPolicy.restore(
                List.of(commercial, operations, technical, commercialChild, technicalChild), artifact(table));
        assertThat(restored.get(1).parentCode()).isEqualTo("commercial");
        assertThat(restored.get(3).parentCode()).isEqualTo("operations");
        assertThat(restored.get(4).parentCode()).isEqualTo("technical");
        var wrongParent = technicalChild.withParentCode("operations");
        assertThat(SourceScoreHierarchyPolicy.restore(List.of(commercial, operations, technical, wrongParent),
                artifact(table)).getLast().parentCode()).isEqualTo("technical");
    }

    @Test void groupedTotalMustNotCombineIndependentSourceDocuments() {
        // Neither a shared title, shared ID alone, nor shared digest alone establishes one source.
        for (var identities : List.of(
                List.of(new DocumentArtifact.ArtifactMeta("a", "sha-a", "PDF", "2.2"),
                        new DocumentArtifact.ArtifactMeta("b", "sha-b", "PDF", "2.2"),
                        new DocumentArtifact.ArtifactMeta("c", "sha-c", "PDF", "2.2")),
                List.of(new DocumentArtifact.ArtifactMeta("same", "sha-a", "PDF", "2.2"),
                        new DocumentArtifact.ArtifactMeta("same", "sha-b", "PDF", "2.2"),
                        new DocumentArtifact.ArtifactMeta("same", "sha-c", "PDF", "2.2")),
                List.of(new DocumentArtifact.ArtifactMeta("a", "same-sha", "PDF", "2.2"),
                        new DocumentArtifact.ArtifactMeta("b", "same-sha", "PDF", "2.2"),
                        new DocumentArtifact.ArtifactMeta("c", "same-sha", "PDF", "2.2")))) {
            var bundle = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bundle", "bundle-sha", "PDF", "2.2"),
                    List.of(groupTable("commercial", 1, "商务评分（20分）", "20", "商务细则"),
                            groupTable("technical", 2, "技术评分（50分）", "50", "技术细则"),
                            groupTable("price", 3, "价格评分（30分）", "30", "价格细则")),
                    List.of(), List.of(), "2.2", List.of(
                            new DocumentArtifact.SourceDocument("commercial", identities.get(0)),
                            new DocumentArtifact.SourceDocument("technical", identities.get(1)),
                            new DocumentArtifact.SourceDocument("price", identities.get(2))));
            var total = OriginalScoringContext.resolve(bundle, "# " + SECTION).total();
            assertThat(total.value()).as("source documents %s", identities).isNull();
            assertThat(total.source()).isEqualTo("UNKNOWN");
            assertThat(total.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                    .contains("ORIGINAL_TOTAL_UNKNOWN");
        }
    }

    @Test void independentlyScoredSubrulesInOneCellRemainSeparate() {
        String rule = "提供质量证书得5分；提供安全证书得5分。";
        var artifact = artifact(table("t", 1, List.of(cell("name", 1, 0, "证书能力", 1),
                cell("weight", 1, 1, "5", 1), cell("rule", 1, 2, rule, 1))));
        var first = fact("quality", "", "质量", "提供质量证书得5分", rule, List.of("rule"), 5);
        var second = fact("safety", "", "安全", "提供安全证书得5分", rule, List.of("rule"), 5);
        assertThat(SourceFactDeduplication.merge(List.of(first, second), artifact)).hasSize(2);
    }

    @Test void punctuationRepairMustNotAcceptChangedNumericConditions() {
        String raw = "整体配送方案：须包含配送网络、车辆及人员清单；优秀得6分，未提供不得分。";
        var artifact = artifact(table("t", 1, List.of(cell("name", 1, 0, "整体配送", 1),
                cell("weight", 1, 1, "6", 1), cell("rule", 1, 2, raw, 1))));
        var input = fact("delivery", "", "技术", "配送要求", raw.replace('：', ':').replace("得6分", "得8分"), List.of(), 6);
        assertThat(PdfFullRuleQuoteBinder.bind(input, artifact, OriginalScoringContext.schemes(artifact, ""))).isEmpty();
    }

    @Test void headersMustNotCrossSourceDocumentOrSchemeBoundary() {
        var header = groupTable("head", 1, "商务评分（20分）", "20", "商务细则");
        var other = new CanonicalTable("other", 2, "第五章 合同履约考核", 1, 4, List.of(), List.of(
                cell("other-rule", 0, 3, "优秀得6分。", 2)), true, "PDF");
        assertThat(SourceTableHeaderPolicy.headers(artifact(header, other), other)).isEmpty();
        var sameTitleOtherDocument = new CanonicalTable("doc1-t", 2, SECTION, 1, 4, List.of(), other.cells(), true, "PDF");
        var bundle = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bundle", "bundle", "PDF", "2.2"),
                List.of(header, sameTitleOtherDocument), List.of(), List.of(), "2.2", List.of(
                new DocumentArtifact.SourceDocument("head", new DocumentArtifact.ArtifactMeta("a", "sha-a", "PDF", "2.2")),
                new DocumentArtifact.SourceDocument("doc1-", new DocumentArtifact.ArtifactMeta("b", "sha-b", "PDF", "2.2"))));
        assertThat(SourceTableHeaderPolicy.headers(bundle, sameTitleOtherDocument)).isEmpty();
    }

    @Test void scoringQuoteMustNotBeMadeAmbiguousByEarlierQualificationCopy() {
        var actual = table("t", 5, List.of(cell("name", 1, 0, "结算方式", 5),
                cell("weight", 1, 1, "1", 5), cell("rule", 1, 2, RULE, 5)));
        var qualification = new CanonicalTable("notice", 1, "第一章 采购公告", 1, 3, List.of(),
                List.of(cell("notice-rule", 0, 2, RULE, 1)), false, "PDF");
        var artifact = artifact(qualification, actual);
        var input = fact("settlement", "", "商务", RULE, RULE, List.of(), 1).withScope(
                OriginalScoringContext.schemeId(SECTION), "");
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(input), artifact, "").getFirst().tableId()).isEqualTo("t");
    }

    static CanonicalTable groupTable(String id, int page, String group, String weight, String rule) {
        return new CanonicalTable(id, page, SECTION, 2, 4, List.of(), List.of(
                cell(id + "h0", 0, 0, "评审项目", page), cell(id + "h1", 0, 1, "评审项目", page),
                cell(id + "h2", 0, 2, "分值", page), cell(id + "h3", 0, 3, "评分标准", page),
                cell(id + "g", 1, 0, group, page), cell(id + "n", 1, 1, rule, page),
                cell(id + "w", 1, 2, weight, page), cell(id + "r", 1, 3, rule + "，满分" + weight + "分。", page)).stream().map(c -> new CanonicalCellDto(
                        c.cellId(), c.row(), c.column(), c.rawText(), c.normalizedText(), c.rowspan(), c.colspan(),
                        c.isMergedContinue(), c.sourceXmlPath(), c.sourcePage(), id + "-r" + c.row())).toList(), true, "PDF");
    }
    static DocumentArtifact artifact(CanonicalTable... tables) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "synthetic-sha", "PDF", "2.2"), List.of(tables), List.of());
    }
    static CanonicalTable table(String id, int page, List<CanonicalCellDto> rows) {
        var cells = new java.util.ArrayList<CanonicalCellDto>();
        cells.add(cell(id + "h0", 0, 0, "评审项目", page));
        cells.add(cell(id + "h1", 0, 1, "分值", page));
        cells.add(cell(id + "h2", 0, 2, "评分标准", page));
        cells.addAll(rows);
        int count = rows.stream().mapToInt(CanonicalCellDto::row).max().orElse(0) + 1;
        return new CanonicalTable(id, page, SECTION, count, 3, List.of(), cells, true, "PDF");
    }
    static CanonicalCellDto cell(String id, int row, int column, String raw, int page) {
        return new CanonicalCellDto(id, row, column, raw, raw, 1, 1, false, "pdf:page=" + page, page, "row" + row);
    }
    static MultimodalScoreFact fact(String code, String parent, String dimension, String rule, String quote, List<String> ids, int weight) {
        return new MultimodalScoreFact(SECTION, code, parent, dimension, BigDecimal.valueOf(weight), rule,
                "SUBJECTIVE", 1, ids.isEmpty() ? "" : "t", ids, quote, .9, ChildAggregation.UNKNOWN,
                OriginalScoringContext.schemeId(SECTION), "IN_SCHEME");
    }
}
