// Tests for ScoreFact, ScoreEvidence, ScoreFactValidator and deterministic extraction.
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.MergedRegionDto;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicScoreFactExtractorTest {

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
        // Origin row: dim cell has rowspan=2, so parentFactId stays empty —
        // but the child references it, so the origin is a PARENT, not a leaf.
        assertEquals("", originFact.parentFactId());
        assertFalse(ScoreFact.isLeaf(originFact, facts));
        // Child row: dim cell is a merge continuation; must reference the origin row's factId.
        assertEquals(originFact.factId(), childFact.parentFactId());
        assertTrue(ScoreFact.isLeaf(childFact, facts));
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
}
