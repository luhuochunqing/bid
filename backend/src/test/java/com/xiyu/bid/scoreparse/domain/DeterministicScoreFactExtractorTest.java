// Tests for ScoreFact, ScoreEvidence, ScoreFactValidator and deterministic extraction.
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.CandidateInput;
import com.xiyu.bid.scoreparse.domain.ScoreReviewAdoptionPolicy.AdoptionResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicScoreFactExtractorTest {

    @Test void fullWidthReviewerInstructionsAreNotItemsButScoringConditionsRemain() {
        for (String text : List.of("注：评委独立评分，按算术平均分汇总。", "注：评委独立评分，提供证书得5分。")) {
            var cells = List.of(cell("h0", 0, 0, "序号"), cell("h1", 0, 1, "评分因素"),
                    cell("h2", 0, 2, "评分细则"), cell("h3", 0, 3, "分值"),
                    cellWithMerge("note", 1, 0, text, 1, 4, false),
                    cellWithMerge("n1", 1, 1, "", 1, 1, true), cellWithMerge("n2", 1, 2, "", 1, 1, true),
                    cellWithMerge("n3", 1, 3, "", 1, 1, true));
            var table = gridWithMergedRegions(cells, List.of(new MergedRegionDto("note", 1, 4)), 2, 4);
            assertTrue(table.isStructurallyValid());
            var facts = DeterministicScoreFactExtractor.extract(table, mapping());
            assertEquals(text.contains("得5分") ? 1 : 0, facts.size());
        }
    }

    @Test
    void truncatedCategoryWithoutLeafTitleDoesNotBecomePublishableName() {
        var table = grid(List.of(
                cell("h0", 0, 0, "序号"), cell("h1", 0, 1, "评分因素"),
                cell("h2", 0, 2, "评分细则"), cell("h3", 0, 3, "分值"),
                cell("c0", 1, 0, "1"), cell("c1", 1, 1, "商务评分（20"),
                cell("c2", 1, 2, "投标人提供营业收入证明得30分"), cell("c3", 1, 3, "30")
        ), 2, 4);

        var facts = DeterministicScoreFactExtractor.extract(table, mapping());
        assertEquals(1, facts.size());
        assertEquals(new BigDecimal("30"), facts.getFirst().weight());
        assertEquals("", facts.getFirst().dimension());
        var scope = new OriginalScoreEvidencePolicy.Qualification(facts.getFirst().factId(), "scheme", "IN_SCHEME", true, "OK", "");
        var snapshot = ScoreExtractionSnapshot.from("task", facts, List.of(scope), List.of());
        assertFalse(snapshot.items().getFirst().computable());
        assertTrue(snapshot.items().getFirst().diagnostics().contains("INVALID_SCORE_ITEM_NAME"));
    }

    private static CanonicalCellDto cell(String id, int row, int column, String text) {
        return new CanonicalCellDto(
                id, row, column, text, text, 1, 1, false,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    /**
     * Variant of {@link #cell} that exposes rowspan/colspan/isMergedContinue so the
     * parent-linkage tests can build vmerged origins and their continuations.
     */
    private static CanonicalCellDto cellWithMerge(
            String id, int row, int column, String text,
            int rowspan, int colspan, boolean isMergedContinue) {
        return new CanonicalCellDto(
                id, row, column, text, text, rowspan, colspan, isMergedContinue,
                "/w:document[1]/w:body[1]/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (column + 1) + "]");
    }

    private static CanonicalTableGrid grid(List<CanonicalCellDto> cells, int rows, int columns) {
        return CanonicalTableGridFactory.fromSidecarTable(new CanonicalTable(
                "t-0001", null, "评分标准", rows, columns, List.of(), cells, true));
    }

    /**
     * Grid builder that also accepts merged regions — needed for the parent-linkage tests.
     */
    private static CanonicalTableGrid gridWithMergedRegions(
            List<CanonicalCellDto> cells,
            List<MergedRegionDto> regions,
            int rows,
            int columns) {
        return CanonicalTableGridFactory.fromSidecarTable(new CanonicalTable(
                "t-0001", null, "评分标准", rows, columns, regions, cells, true));
    }

    private static TableColumnMapping mapping() {
        return new TableColumnMapping(0, -1, 1, 2, 3, 1, false);
    }

    private static CanonicalTableGrid mergedOriginGrid(
            String title, String firstDetail, String continuationDetail, String continuationWeight) {
        return gridWithMergedRegions(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评审要素"),
                cell("t-0001-r0-c2", 0, 2, "评审内容"),
                cell("t-0001-r0-c3", 0, 3, "最高分"),
                cellWithMerge("t-0001-r1-c0", 1, 0, "A1", 2, 1, false),
                cellWithMerge("t-0001-r1-c1", 1, 1, title, 2, 1, false),
                cell("t-0001-r1-c2", 1, 2, firstDetail),
                cell("t-0001-r1-c3", 1, 3, "5"),
                cellWithMerge("t-0001-r2-c0", 2, 0, "", 1, 1, true),
                cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                cell("t-0001-r2-c2", 2, 2, continuationDetail),
                cell("t-0001-r2-c3", 2, 3, continuationWeight)
        ), List.of(
                new MergedRegionDto("t-0001-r1-c1", 2, 1),
                new MergedRegionDto("t-0001-r1-c0", 2, 1)
        ), 3, 4);
    }

    @Test
    void merged_origin_with_group_total_and_first_leaf_emits_both_facts() {
        CanonicalTableGrid grid = mergedOriginGrid(
                "履约保障（10分）", "仓储能力满足要求", "配送能力满足要求", "5");

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());

        assertEquals(3, facts.size(), "one physical origin row carries parent and first-leaf semantics");
        ScoreFact parent = facts.get(0);
        ScoreFact firstLeaf = facts.get(1);
        ScoreFact continuationLeaf = facts.get(2);

        assertEquals("履约保障", parent.dimension());
        assertEquals(0, parent.weight().compareTo(new BigDecimal("10")));
        assertEquals(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, parent.weightEvidence().kind());
        assertEquals(ScoreFact.ValidationStatus.REVIEW_REQUIRED, parent.validationStatus());

        assertEquals(0, firstLeaf.weight().compareTo(new BigDecimal("5")));
        assertEquals(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, firstLeaf.weightEvidence().kind());
        assertEquals(parent.factId(), firstLeaf.parentFactId());
        assertEquals(parent.factId(), continuationLeaf.parentFactId());
        assertEquals("履约保障", firstLeaf.dimension());
        assertEquals("履约保障", continuationLeaf.dimension(), "continuation leaf must inherit dimension from origin");
        assertNotEquals(parent.factId(), firstLeaf.factId());
        assertEquals(facts, DeterministicScoreFactExtractor.extract(grid, mapping()),
                "repeated extraction must keep role-aware fact IDs stable");
        assertFalse(ScoreFact.isLeaf(parent, facts));
        assertTrue(ScoreFact.isLeaf(firstLeaf, facts));
        assertTrue(ScoreFact.isLeaf(continuationLeaf, facts));
    }

    @Test
    void merged_origin_requires_unambiguous_title_first_detail_and_scored_continuation() {
        List<ScoreFact> ambiguousTitle = DeterministicScoreFactExtractor.extract(
                mergedOriginGrid("履约保障（10分）（20分）", "仓储能力", "配送能力", "5"), mapping());
        List<ScoreFact> blankFirstDetail = DeterministicScoreFactExtractor.extract(
                mergedOriginGrid("履约保障（10分）", "", "配送能力", "5"), mapping());
        List<ScoreFact> unscoredContinuation = DeterministicScoreFactExtractor.extract(
                mergedOriginGrid("履约保障（10分）", "仓储能力", "配送能力", ""), mapping());

        assertEquals(2, ambiguousTitle.size(), "ambiguous title total must not invent a parent fact");
        assertEquals(2, blankFirstDetail.size(), "blank origin detail must not invent a first leaf");
        assertEquals(2, unscoredContinuation.size(), "an unscored continuation does not prove a group");
    }

    @Test
    void extracts_explicit_weight_with_exact_cell_evidence() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A1"),
                cell("t-0001-r1-c1", 1, 1, "财务状况"),
                cell("t-0001-r1-c2", 1, 2, "速动比率达到要求"),
                cell("t-0001-r1-c3", 1, 3, "5分")
        ), 2, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());

        assertEquals(1, facts.size());
        ScoreFact fact = facts.get(0);
        assertEquals(new BigDecimal("5"), fact.weight());
        assertEquals(ScoreFact.ValidationStatus.AUTO_APPROVED, fact.validationStatus());
        assertEquals(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, fact.weightEvidence().kind());
        assertEquals("t-0001-r1-c3", fact.weightEvidence().cellId());
        assertTrue(fact.weightEvidence().isLocatable());
        assertTrue(fact.sourceCellIds().contains("t-0001-r1-c2"));
    }

    @Test
    void extracts_title_maximum_when_weight_column_is_blank() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A2"),
                cell("t-0001-r1-c1", 1, 1, "营业收现比率（6分）"),
                cell("t-0001-r1-c2", 1, 2, "满足条件"),
                cell("t-0001-r1-c3", 1, 3, "")
        ), 2, 4);

        ScoreFact fact = DeterministicScoreFactExtractor.extract(grid, mapping()).get(0);

        assertEquals(new BigDecimal("6"), fact.weight());
        assertEquals(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, fact.weightEvidence().kind());
        assertEquals("（6分）", fact.weightEvidence().quotedText());
        assertTrue(fact.weightEvidence().isLocatable());
    }

    @Test
    void extracts_title_maximum_with_newline_and_normalizes_quoted_text() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A2"),
                cell("t-0001-r1-c1", 1, 1, "商务及技术评分（10\n分）"),
                cell("t-0001-r1-c2", 1, 2, "满足条件"),
                cell("t-0001-r1-c3", 1, 3, "")
        ), 2, 4);

        ScoreFact fact = DeterministicScoreFactExtractor.extract(grid, mapping()).get(0);

        assertEquals(new BigDecimal("10"), fact.weight());
        assertEquals(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, fact.weightEvidence().kind());
        assertEquals("（10 分）", fact.weightEvidence().quotedText());
        assertTrue(fact.weightEvidence().isLocatable());
    }

    @Test
    void detail_text_containing_summary_words_is_not_skipped_as_summary_row() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A5"),
                cell("t-0001-r1-c1", 1, 1, "综合评价（10分）"),
                cell("t-0001-r1-c2", 1, 2, "按各子项合计得分进行综合评价。"),
                cell("t-0001-r1-c3", 1, 3, "")
        ), 2, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());

        assertEquals(1, facts.size());
        assertEquals("综合评价", facts.get(0).dimension());
        assertEquals(new BigDecimal("10"), facts.get(0).weight());
    }

    @Test
    void misaligned_summary_row_does_not_leak_into_facts() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "合计"),
                cell("t-0001-r1-c1", 1, 1, "12"),
                cell("t-0001-r1-c2", 1, 2, "100"),
                cell("t-0001-r1-c3", 1, 3, "")
        ), 2, 4);

        assertTrue(DeterministicScoreFactExtractor.extract(grid, mapping()).isEmpty());
    }

    @Test
    void rule_award_value_is_not_used_as_item_weight() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A3"),
                cell("t-0001-r1-c1", 1, 1, "信用评价"),
                cell("t-0001-r1-c2", 1, 2, "近三年无处罚得6分，存在处罚扣2分"),
                cell("t-0001-r1-c3", 1, 3, "")
        ), 2, 4);

        ScoreFact fact = DeterministicScoreFactExtractor.extract(grid, mapping()).get(0);

        assertEquals(null, fact.weight());
        assertEquals(ScoreFact.ValidationStatus.REVIEW_REQUIRED, fact.validationStatus());
        assertEquals(ScoreRule.DEDUCTION, fact.scoreRule());
        assertFalse(fact.isAutoApprovable());
    }

    @Test
    void missing_weight_is_review_required_and_not_auto_candidate() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A4"),
                cell("t-0001-r1-c1", 1, 1, "服务能力"),
                cell("t-0001-r1-c2", 1, 2, "响应时间满足要求"),
                cell("t-0001-r1-c3", 1, 3, "")
        ), 2, 4);

        ScoreFact fact = DeterministicScoreFactExtractor.extract(grid, mapping()).get(0);

        assertEquals(null, fact.weight());
        assertEquals(ScoreFact.ValidationStatus.REVIEW_REQUIRED, fact.validationStatus());
        assertTrue(fact.toCandidate().isEmpty());
    }

    @Test
    void invalid_structure_yields_no_facts() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号")
        ), 2, 2);

        assertTrue(DeterministicScoreFactExtractor.extract(grid, mapping()).isEmpty());
    }

    @Test
    void validator_separates_fact_validity_from_scoring_completeness() {
        ScoreFact fact = new ScoreFact(
                "fact-1", "t-1", List.of("t-1-r0-c0"), "技术方案 | 5分", "A1", "技术方案",
                new BigDecimal("5"),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "5分", "t-1-r0-c0", 7, 9, "column"),
                ScoreRule.MAX_SCORE, "", "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED);

        ScoreFactValidator.ValidationResult facts = ScoreFactValidator.validateFacts(List.of(fact));
        ScoreFactValidator.ValidationResult scoring = ScoreFactValidator.validateScoringSet(List.of(fact));

        assertTrue(facts.factsValid());
        assertFalse(facts.scoringComplete());
        assertTrue(scoring.factsValid());
        assertTrue(scoring.scoringComplete());
        assertTrue(scoring.violations().isEmpty());
    }

    @Test
    void validator_rejects_referenced_parent_from_auto_scoring_set() {
        // 父项 = 被子项 parentFactId 引用的 fact；它即使标了 AUTO_APPROVED
        // 也不得进入自动评分集（叶子 = 未被引用）。
        ScoreFact parent = new ScoreFact(
                "fact-parent", "t-1", List.of("t-1-r0-c0"), "商务部分（20分）", "", "商务部分",
                new BigDecimal("20"),
                new ScoreEvidence(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, "（20分）", "t-1-r0-c0", 4, 9, "title"),
                ScoreRule.MAX_SCORE, "", "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED);
        ScoreFact child = new ScoreFact(
                "fact-child", "t-1", List.of("t-1-r1-c0"), "条款1得5分", "B1", "条款1",
                new BigDecimal("10"),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "10", "t-1-r1-c0", 0, 2, "column"),
                ScoreRule.MAX_SCORE, "fact-parent", "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED);

        ScoreFactValidator.ValidationResult result = ScoreFactValidator.validateScoringSet(List.of(parent, child));

        assertFalse(result.factsValid());
        assertFalse(result.scoringComplete());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.contains("fact-parent") && v.contains("parent fact")),
                "被引用的父项必须被拦截: " + result.violations());
        assertFalse(result.violations().stream().anyMatch(v -> v.contains("fact-child")),
                "叶子子项必须放行: " + result.violations());
    }

    @Test
    void validator_allows_leaf_child_and_non_auto_parent_in_scoring_set() {
        // 父项转 REVIEW_REQUIRED 保留快照；叶子子项 AUTO_APPROVED 可自动评分。
        ScoreFact parent = new ScoreFact(
                "fact-parent", "t-1", List.of("t-1-r0-c0"), "商务部分（20分）", "", "商务部分",
                new BigDecimal("20"),
                new ScoreEvidence(ScoreEvidence.Kind.DIMENSION_TITLE_MAX_SCORE, "（20分）", "t-1-r0-c0", 4, 9, "title"),
                ScoreRule.MAX_SCORE, "", "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.REVIEW_REQUIRED);
        ScoreFact child = new ScoreFact(
                "fact-child", "t-1", List.of("t-1-r1-c0"), "条款1得5分", "B1", "条款1",
                new BigDecimal("10"),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, "10", "t-1-r1-c0", 0, 2, "column"),
                ScoreRule.MAX_SCORE, "fact-parent", "STRUCTURED_TABLE_DETERMINISTIC", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED);

        ScoreFactValidator.ValidationResult result = ScoreFactValidator.validateScoringSet(List.of(parent, child));

        assertTrue(result.factsValid());
        assertTrue(result.scoringComplete());
        assertTrue(result.violations().isEmpty());
        assertTrue(ScoreFact.isLeaf(child, List.of(parent, child)));
        assertFalse(ScoreFact.isLeaf(parent, List.of(parent, child)));
    }

    // ------------------------------------------------------------------
    // Parent-linkage regression tests (spec 059 Phase 2 fix).
    //
    // The previous implementation only saw rowCells (the current row's cells),
    // so cell.row() < row was never true and every non-origin row got
    // parentFactId == "". These tests pin down the correct semantic:
    // a row whose dimCell is a merge continuation references the origin
    // row's factId; origin and non-merged rows have no parent.
    // ------------------------------------------------------------------

    @Test
    void parent_linkage_continuation_row_points_to_origin_factId() {
        // Row 1: dim cell "商务部分" spans 2 rows (origin).
        // Row 2: dim cell is a merge continuation; the child fact's parent
        //        must equal the origin row's factId.
        CanonicalTableGrid grid = gridWithMergedRegions(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cellWithMerge("t-0001-r1-c0", 1, 0, "M1", 1, 1, false),
                cellWithMerge("t-0001-r1-c1", 1, 1, "商务部分", 2, 1, false),
                cellWithMerge("t-0001-r1-c2", 1, 2, "条款1得5分", 1, 1, false),
                cellWithMerge("t-0001-r1-c3", 1, 3, "10", 1, 1, false),
                cellWithMerge("t-0001-r2-c0", 2, 0, "M2", 1, 1, false),
                cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                cellWithMerge("t-0001-r2-c2", 2, 2, "条款2得5分", 1, 1, false),
                cellWithMerge("t-0001-r2-c3", 2, 3, "10", 1, 1, false)
        ), List.of(new MergedRegionDto("t-0001-r1-c1", 2, 1)), 3, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(2, facts.size(), "expected 2 facts (one per non-header row)");

        ScoreFact originFact = facts.get(0);
        ScoreFact childFact = facts.get(1);
        // 合并单元格无汇总分值：起点提供分类维度名称，但起点是正常计分明细，绝不能作为汇总父项
        assertEquals("", originFact.parentFactId());
        assertTrue(ScoreFact.isLeaf(originFact, facts), "origin row with explicit score is a scored leaf, not a summary parent");
        assertEquals("商务部分", originFact.dimension());

        // 续行：继承起点的维度名称“商务部分”，但业务父子关系与版式继承分离，parentFactId 保持为空
        assertEquals("", childFact.parentFactId(), "continuation leaf must not treat preceding leaf as parent");
        assertTrue(ScoreFact.isLeaf(childFact, facts));
        assertEquals("商务部分", childFact.dimension(), "continuation leaf must inherit dimension name");
    }

    @Test
    void parent_linkage_origin_row_has_empty_parent() {
        // A vmerged origin (dim cell rowspan=2) with a continuation row that
        // has all-blank source text. The continuation row is filtered out by
        // the sourceText.isBlank() guard, leaving only the origin fact.
        // The origin row's parentFactId must be "" because it IS the parent.
        CanonicalTableGrid grid = gridWithMergedRegions(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "P1"),
                cellWithMerge("t-0001-r1-c1", 1, 1, "商务部分（10分）", 2, 1, false),
                cell("t-0001-r1-c2", 1, 2, "条款1得5分"),
                cell("t-0001-r1-c3", 1, 3, "10"),
                cell("t-0001-r2-c0", 2, 0, ""),
                cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                cell("t-0001-r2-c2", 2, 2, ""),
                cell("t-0001-r2-c3", 2, 3, "")
        ), List.of(new MergedRegionDto("t-0001-r1-c1", 2, 1)), 3, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(1, facts.size(),
                "only the origin row should produce a fact; continuation row is filtered for blank sourceText");
        assertEquals("", facts.get(0).parentFactId());
        // 唯一 fact、无任何引用 → 叶子。
        assertTrue(ScoreFact.isLeaf(facts.get(0), facts));
    }

    @Test
    void parent_linkage_no_merged_cells_all_facts_are_leaves() {
        // No merged regions anywhere: every fact's parentFactId must be empty,
        // and every fact must be a leaf.
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A1"),
                cell("t-0001-r1-c1", 1, 1, "财务状况"),
                cell("t-0001-r1-c2", 1, 2, "速动比率达标"),
                cell("t-0001-r1-c3", 1, 3, "5"),
                cell("t-0001-r2-c0", 2, 0, "A2"),
                cell("t-0001-r2-c1", 2, 1, "技术方案"),
                cell("t-0001-r2-c2", 2, 2, "完整覆盖"),
                cell("t-0001-r2-c3", 2, 3, "10"),
                cell("t-0001-r3-c0", 3, 0, "A3"),
                cell("t-0001-r3-c1", 3, 1, "服务能力"),
                cell("t-0001-r3-c2", 3, 2, "响应及时"),
                cell("t-0001-r3-c3", 3, 3, "3")
        ), 4, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(3, facts.size());
        for (ScoreFact fact : facts) {
            assertEquals("", fact.parentFactId(), "no-merge fact should have empty parentFactId");
            assertTrue(ScoreFact.isLeaf(fact, facts));
        }
    }

    @Test
    void extract_skips_unmerged_empty_dimension_and_unscored_overflow_row() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "A1"),
                cell("t-0001-r1-c1", 1, 1, "财务状况"),
                cell("t-0001-r1-c2", 1, 2, "速动比率达标"),
                cell("t-0001-r1-c3", 1, 3, "5"),
                // 跨页截断/溢出残行：无编号、无维度、无分值，仅细则列有文本
                cell("t-0001-r2-c0", 2, 0, ""),
                cell("t-0001-r2-c1", 2, 1, ""),
                cell("t-0001-r2-c2", 2, 2, "补充说明跨页残行"),
                cell("t-0001-r2-c3", 2, 3, ""),
                cell("t-0001-r3-c0", 3, 0, "A2"),
                cell("t-0001-r3-c1", 3, 1, "技术方案"),
                cell("t-0001-r3-c2", 3, 2, "完整覆盖"),
                cell("t-0001-r3-c3", 3, 3, "10")
        ), 4, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(2, facts.size(), "overflow line without dimension and weight should be skipped");
        assertEquals("A1", facts.get(0).code());
        assertEquals("A2", facts.get(1).code());
    }

    private static List<CandidateInput> toCandidateInputs(List<ScoreFact> facts) {
        return facts.stream()
                .map(f -> new CandidateInput(
                        f.factId(), f.code(), f.dimension(), f.weight(), f.sourceText(), f.parentFactId()))
                .toList();
    }

    @Test
    void extraction_and_adoption_chain_unmerged_labels_three_leaves_100_points() {
        CanonicalTableGrid grid = grid(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cell("t-0001-r1-c0", 1, 0, "1"),
                cell("t-0001-r1-c1", 1, 1, "商务"),
                cell("t-0001-r1-c2", 1, 2, "财务状况"),
                cell("t-0001-r1-c3", 1, 3, "30"),
                cell("t-0001-r2-c0", 2, 0, "2"),
                cell("t-0001-r2-c1", 2, 1, "商务"),
                cell("t-0001-r2-c2", 2, 2, "履约能力"),
                cell("t-0001-r2-c3", 2, 3, "20"),
                cell("t-0001-r3-c0", 3, 0, "3"),
                cell("t-0001-r3-c1", 3, 1, "价格"),
                cell("t-0001-r3-c2", 3, 2, "投标报价"),
                cell("t-0001-r3-c3", 3, 3, "50")
        ), 4, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(3, facts.size(), "unmerged table must produce exactly 3 facts");
        assertEquals("1", facts.get(0).code());
        assertEquals("商务", facts.get(0).dimension());
        assertEquals(0, facts.get(0).weight().compareTo(new BigDecimal("30")));
        assertEquals("", facts.get(0).parentFactId());

        assertEquals("2", facts.get(1).code());
        assertEquals("商务", facts.get(1).dimension());
        assertEquals(0, facts.get(1).weight().compareTo(new BigDecimal("20")));
        assertEquals("", facts.get(1).parentFactId());

        assertEquals("3", facts.get(2).code());
        assertEquals("价格", facts.get(2).dimension());
        assertEquals(0, facts.get(2).weight().compareTo(new BigDecimal("50")));
        assertEquals("", facts.get(2).parentFactId());

        ScoreReviewAdoptionPolicy policy = new ScoreReviewAdoptionPolicy();
        AdoptionResult result = policy.adopt(toCandidateInputs(facts), Set.of(), Map.of());
        assertTrue(result.success());
        assertEquals(0, result.totalWeight().compareTo(new BigDecimal("100")));
        assertEquals(3, result.candidates().size());
        assertEquals("1", result.candidates().get(0).code());
        assertEquals(0, result.candidates().get(0).weight().compareTo(new BigDecimal("30")));
        assertEquals("2", result.candidates().get(1).code());
        assertEquals(0, result.candidates().get(1).weight().compareTo(new BigDecimal("20")));
        assertEquals("3", result.candidates().get(2).code());
        assertEquals(0, result.candidates().get(2).weight().compareTo(new BigDecimal("50")));
    }

    @Test
    void extraction_and_adoption_chain_merged_label_without_summary_weight_three_leaves_100_points() {
        CanonicalTableGrid grid = gridWithMergedRegions(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cellWithMerge("t-0001-r1-c0", 1, 0, "1", 1, 1, false),
                cellWithMerge("t-0001-r1-c1", 1, 1, "商务", 2, 1, false),
                cell("t-0001-r1-c2", 1, 2, "财务状况"),
                cell("t-0001-r1-c3", 1, 3, "30"),
                cellWithMerge("t-0001-r2-c0", 2, 0, "2", 1, 1, false),
                cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                cell("t-0001-r2-c2", 2, 2, "履约能力"),
                cell("t-0001-r2-c3", 2, 3, "20"),
                cell("t-0001-r3-c0", 3, 0, "3"),
                cell("t-0001-r3-c1", 3, 1, "价格"),
                cell("t-0001-r3-c2", 3, 2, "投标报价"),
                cell("t-0001-r3-c3", 3, 3, "50")
        ), List.of(
                new MergedRegionDto("t-0001-r1-c1", 2, 1)
        ), 4, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(3, facts.size(), "merged label without summary weight must produce 3 facts");

        ScoreFact f1 = facts.get(0);
        assertEquals("1", f1.code());
        assertEquals("商务", f1.dimension());
        assertEquals(0, f1.weight().compareTo(new BigDecimal("30")));
        assertEquals("", f1.parentFactId(), "first leaf must not have parentFactId");
        assertTrue(ScoreFact.isLeaf(f1, facts));

        ScoreFact f2 = facts.get(1);
        assertEquals("2", f2.code());
        assertEquals("商务", f2.dimension(), "continuation leaf must inherit dimension '商务'");
        assertEquals(0, f2.weight().compareTo(new BigDecimal("20")));
        assertEquals("", f2.parentFactId(), "continuation leaf must not treat first leaf as parent");
        assertTrue(ScoreFact.isLeaf(f2, facts));

        ScoreFact f3 = facts.get(2);
        assertEquals("3", f3.code());
        assertEquals("价格", f3.dimension());
        assertEquals(0, f3.weight().compareTo(new BigDecimal("50")));
        assertEquals("", f3.parentFactId());
        assertTrue(ScoreFact.isLeaf(f3, facts));

        ScoreReviewAdoptionPolicy policy = new ScoreReviewAdoptionPolicy();
        AdoptionResult result = policy.adopt(toCandidateInputs(facts), Set.of(), Map.of());
        assertTrue(result.success());
        assertEquals(3, result.candidates().size(), "adoption must retain all 3 items, not 2");
        assertEquals(0, result.totalWeight().compareTo(new BigDecimal("100")), "total weight must be 100, not 70");
        assertEquals("1", result.candidates().get(0).code());
        assertEquals(0, result.candidates().get(0).weight().compareTo(new BigDecimal("30")));
        assertEquals("2", result.candidates().get(1).code());
        assertEquals(0, result.candidates().get(1).weight().compareTo(new BigDecimal("20")));
        assertEquals("3", result.candidates().get(2).code());
        assertEquals(0, result.candidates().get(2).weight().compareTo(new BigDecimal("50")));
    }

    @Test
    void extraction_and_adoption_chain_merged_label_with_summary_weight_three_leaves_100_points() {
        CanonicalTableGrid grid = gridWithMergedRegions(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                cellWithMerge("t-0001-r1-c0", 1, 0, "1", 1, 1, false),
                cellWithMerge("t-0001-r1-c1", 1, 1, "商务（50分）", 2, 1, false),
                cell("t-0001-r1-c2", 1, 2, "财务状况"),
                cell("t-0001-r1-c3", 1, 3, "30"),
                cellWithMerge("t-0001-r2-c0", 2, 0, "2", 1, 1, false),
                cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                cell("t-0001-r2-c2", 2, 2, "履约能力"),
                cell("t-0001-r2-c3", 2, 3, "20"),
                cell("t-0001-r3-c0", 3, 0, "3"),
                cell("t-0001-r3-c1", 3, 1, "价格"),
                cell("t-0001-r3-c2", 3, 2, "投标报价"),
                cell("t-0001-r3-c3", 3, 3, "50")
        ), List.of(
                new MergedRegionDto("t-0001-r1-c1", 2, 1)
        ), 4, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(4, facts.size(), "must produce 1 parent summary fact + 3 leaf facts");

        ScoreFact parent = facts.get(0);
        assertEquals("", parent.code());
        assertEquals("商务", parent.dimension());
        assertEquals(0, parent.weight().compareTo(new BigDecimal("50")));
        assertEquals("", parent.parentFactId());
        assertFalse(ScoreFact.isLeaf(parent, facts));

        ScoreFact leaf1 = facts.get(1);
        assertEquals("1", leaf1.code());
        assertEquals("商务", leaf1.dimension());
        assertEquals(0, leaf1.weight().compareTo(new BigDecimal("30")));
        assertEquals(parent.factId(), leaf1.parentFactId());
        assertTrue(ScoreFact.isLeaf(leaf1, facts));

        ScoreFact leaf2 = facts.get(2);
        assertEquals("2", leaf2.code());
        assertEquals("商务", leaf2.dimension(), "continuation leaf inherits dimension '商务'");
        assertEquals(0, leaf2.weight().compareTo(new BigDecimal("20")));
        assertEquals(parent.factId(), leaf2.parentFactId(), "continuation leaf parent is summary parent");
        assertTrue(ScoreFact.isLeaf(leaf2, facts));

        ScoreFact leaf3 = facts.get(3);
        assertEquals("3", leaf3.code());
        assertEquals("价格", leaf3.dimension());
        assertEquals(0, leaf3.weight().compareTo(new BigDecimal("50")));
        assertEquals("", leaf3.parentFactId());
        assertTrue(ScoreFact.isLeaf(leaf3, facts));

        ScoreReviewAdoptionPolicy policy = new ScoreReviewAdoptionPolicy();
        AdoptionResult result = policy.adopt(toCandidateInputs(facts), Set.of(), Map.of());
        assertTrue(result.success());
        assertEquals(3, result.candidates().size(), "parent summary 50 is excluded, 3 leaves retained");
        assertEquals(0, result.totalWeight().compareTo(new BigDecimal("100")));
        assertEquals("1", result.candidates().get(0).code());
        assertEquals(0, result.candidates().get(0).weight().compareTo(new BigDecimal("30")));
        assertEquals("2", result.candidates().get(1).code());
        assertEquals(0, result.candidates().get(1).weight().compareTo(new BigDecimal("20")));
        assertEquals("3", result.candidates().get(2).code());
        assertEquals(0, result.candidates().get(2).weight().compareTo(new BigDecimal("50")));
    }

    @Test
    void extraction_and_adoption_chain_standalone_summary_origin_three_leaves_100_points() {
        // 独立商务汇总行50分，下面两条明细30分、20分，另有价格明细50分。
        // 正确结果必须是3个末级项、100分，而不是4项150分。
        CanonicalTableGrid grid = gridWithMergedRegions(List.of(
                cell("t-0001-r0-c0", 0, 0, "编号"),
                cell("t-0001-r0-c1", 0, 1, "评分因素"),
                cell("t-0001-r0-c2", 0, 2, "评分标准"),
                cell("t-0001-r0-c3", 0, 3, "分值"),
                // 行1：独立商务汇总行（评分标准列为空，分值列为50分，评分因素纵向合并跨3行覆盖行1/2/3）
                cellWithMerge("t-0001-r1-c0", 1, 0, "一", 1, 1, false),
                cellWithMerge("t-0001-r1-c1", 1, 1, "商务部分（50分）", 3, 1, false),
                cell("t-0001-r1-c2", 1, 2, ""),
                cell("t-0001-r1-c3", 1, 3, "50"),
                // 行2：明细1（30分）
                cellWithMerge("t-0001-r2-c0", 2, 0, "1", 1, 1, false),
                cellWithMerge("t-0001-r2-c1", 2, 1, "", 1, 1, true),
                cell("t-0001-r2-c2", 2, 2, "财务状况"),
                cell("t-0001-r2-c3", 2, 3, "30"),
                // 行3：明细2（20分）
                cellWithMerge("t-0001-r3-c0", 3, 0, "2", 1, 1, false),
                cellWithMerge("t-0001-r3-c1", 3, 1, "", 1, 1, true),
                cell("t-0001-r3-c2", 3, 2, "履约能力"),
                cell("t-0001-r3-c3", 3, 3, "20"),
                // 行4：价格明细（50分）
                cell("t-0001-r4-c0", 4, 0, "3"),
                cell("t-0001-r4-c1", 4, 1, "价格"),
                cell("t-0001-r4-c2", 4, 2, "投标报价"),
                cell("t-0001-r4-c3", 4, 3, "50")
        ), List.of(
                new MergedRegionDto("t-0001-r1-c1", 3, 1)
        ), 5, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        assertEquals(4, facts.size(), "must produce 1 standalone parent summary fact + 3 leaf facts");

        ScoreFact parent = facts.get(0);
        assertEquals("一", parent.code());
        assertEquals("商务部分", parent.dimension());
        assertEquals(0, parent.weight().compareTo(new BigDecimal("50")));
        assertEquals("", parent.parentFactId());
        assertFalse(ScoreFact.isLeaf(parent, facts), "standalone summary fact must not be leaf");

        ScoreFact leaf1 = facts.get(1);
        assertEquals("1", leaf1.code());
        assertEquals("商务部分", leaf1.dimension(), "continuation leaf inherits dimension from standalone origin");
        assertEquals(0, leaf1.weight().compareTo(new BigDecimal("30")));
        assertEquals(parent.factId(), leaf1.parentFactId(), "leaf1 parentFactId must point to standalone summary parent");
        assertTrue(ScoreFact.isLeaf(leaf1, facts));

        ScoreFact leaf2 = facts.get(2);
        assertEquals("2", leaf2.code());
        assertEquals("商务部分", leaf2.dimension(), "continuation leaf inherits dimension from standalone origin");
        assertEquals(0, leaf2.weight().compareTo(new BigDecimal("20")));
        assertEquals(parent.factId(), leaf2.parentFactId(), "leaf2 parentFactId must point to standalone summary parent");
        assertTrue(ScoreFact.isLeaf(leaf2, facts));

        ScoreFact leaf3 = facts.get(3);
        assertEquals("3", leaf3.code());
        assertEquals("价格", leaf3.dimension());
        assertEquals(0, leaf3.weight().compareTo(new BigDecimal("50")));
        assertEquals("", leaf3.parentFactId());
        assertTrue(ScoreFact.isLeaf(leaf3, facts));

        ScoreReviewAdoptionPolicy policy = new ScoreReviewAdoptionPolicy();
        AdoptionResult result = policy.adopt(toCandidateInputs(facts), Set.of(), Map.of());
        assertTrue(result.success());
        assertEquals(3, result.candidates().size(), "adoption must retain exactly 3 leaves, not 4");
        assertEquals(0, result.totalWeight().compareTo(new BigDecimal("100")), "total weight must be 100, not 150");
        assertEquals("1", result.candidates().get(0).code());
        assertEquals(0, result.candidates().get(0).weight().compareTo(new BigDecimal("30")));
        assertEquals("2", result.candidates().get(1).code());
        assertEquals(0, result.candidates().get(1).weight().compareTo(new BigDecimal("20")));
        assertEquals("3", result.candidates().get(2).code());
        assertEquals(0, result.candidates().get(2).weight().compareTo(new BigDecimal("50")));
    }

    @Test
    @DisplayName("跨行截断括号与竖线前缀不会泄漏进评分项维度名称（project 1394 回归）")
    void refinesSplitBracketCategoryAndPipePrefixIntoCleanLeafDimension() {
        CanonicalTableGrid grid = gridWithMergedRegions(List.of(
                cell("r0c0", 0, 0, "序号"),
                cell("r0c1", 0, 1, "评审大类"),
                cell("r0c2", 0, 2, "评分标准"),
                cell("r0c3", 0, 3, "分值"),
                cellWithMerge("r1c0", 1, 0, "3", 1, 1, false),
                cellWithMerge("r1c1", 1, 1, "商务评分（20\n分）", 2, 1, false),
                cell("r1c2", 1, 2, "3 | 营业总收入：2024年营业总收入大于10亿得5分"),
                cell("r1c3", 1, 3, "5"),
                cellWithMerge("r2c0", 2, 0, "4", 1, 1, false),
                cellWithMerge("r2c1", 2, 1, "", 1, 1, true),
                cell("r2c2", 2, 2, "净资产收益率：净资产收益率大于10%得3分"),
                cell("r2c3", 2, 3, "3")
        ), List.of(
                new MergedRegionDto("r1c1", 2, 1)
        ), 3, 4);

        List<ScoreFact> facts = DeterministicScoreFactExtractor.extract(grid, mapping());
        List<ScoreFact> leaves = facts.stream().filter(f -> ScoreFact.isLeaf(f, facts)).toList();
        assertEquals(2, leaves.size());
        assertEquals("营业总收入", leaves.get(0).dimension());
        assertEquals("净资产收益率", leaves.get(1).dimension());
        facts.forEach(f -> {
            assertFalse(f.dimension().contains("（20"), "dimension must not contain unclosed bracket: " + f.dimension());
            assertFalse(f.dimension().contains("|"), "dimension must not contain table pipe: " + f.dimension());
        });
    }
}

