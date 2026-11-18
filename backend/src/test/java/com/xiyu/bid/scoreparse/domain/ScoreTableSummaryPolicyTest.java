// Input: CanonicalTableGrid score-table summary rows
// Output: cell-anchored declared-total evidence
// Pos: scoreparse/domain test — project 1381 structured-total regression
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGrid;
import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreTableSummaryPolicyTest {

    @Test
    void fullWidthScoreEquationDoesNotBecomeAnItem() {
        var formula = grid(List.of(cell(0, 0, "总分＝价格分＋商务分＋技术分"),
                cell(0, 1, ""), cell(0, 2, "")), 1);
        assertThat(ScoreTableSummaryPolicy.isSummaryRow(formula.cellsInRow(0))).isTrue();
        assertThat(ScoreTableSummaryPolicy.extractDeclaredTotals(formula, mapping())).isEmpty();
        assertThat(ScoreTableSummaryPolicy.isSummaryRow("2", "报价得分＝基准价／报价×35分")).isFalse();
    }

    @Test
    void extractsDeclaredTotalFromProject1381SummaryCells() {
        CanonicalTableGrid grid = grid(List.of(
                cell(0, 0, "序号"), cell(0, 1, "评分因素"), cell(0, 2, "评分标准"),
                cell(1, 0, "1"), cell(1, 1, "投标报价\n（40分）"), cell(1, 2, "报价规则"),
                cell(2, 0, "合计\n得分"), cell(2, 1, "满分\n（100分）"), cell(2, 2, "")
        ), 3);

        List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> evidence =
                ScoreTableSummaryPolicy.extractDeclaredTotals(grid, mapping());

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).value()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(evidence.get(0).tableId()).isEqualTo("p24-t0007");
        assertThat(evidence.get(0).row()).isEqualTo(2);
        assertThat(evidence.get(0).sourceCellId()).isEqualTo("p24-t0007-r2-c1");
        assertThat(evidence.get(0).quotedText()).isEqualTo("100分");
    }

    @Test
    void exposesEveryDistinctScoreInAmbiguousSummaryRow() {
        CanonicalTableGrid grid = grid(List.of(
                cell(0, 0, "序号"), cell(0, 1, "评分因素"), cell(0, 2, "评分标准"),
                cell(1, 0, "总计\n得分"), cell(1, 1, "满分100分，折算30分"), cell(1, 2, "")
        ), 2);

        assertThat(ScoreTableSummaryPolicy.extractDeclaredTotals(grid, mapping()))
                .extracting(ScoreTableSummaryPolicy.DeclaredTotalEvidence::value)
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("30.00"));
    }

    @Test
    void perItemScoreMentionInSummaryRowIsNotDeclaredTotal() {
        CanonicalTableGrid grid = grid(List.of(
                cell(0, 0, "序号"), cell(0, 1, "评分因素"), cell(0, 2, "评分标准"),
                cell(1, 0, "合计"), cell(1, 1, "每项 满分10分，满分100分"), cell(1, 2, "")
        ), 2);

        List<ScoreTableSummaryPolicy.DeclaredTotalEvidence> evidence =
                ScoreTableSummaryPolicy.extractDeclaredTotals(grid, mapping());

        assertThat(evidence).hasSize(1);
        assertThat(evidence.get(0).value()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(evidence.get(0).quotedText()).isEqualTo("100分");
    }

    @Test
    void summaryRecognitionDoesNotMatchNormalDetailLanguage() {
        assertThat(ScoreTableSummaryPolicy.isSummaryRow("A5", "综合评价")).isFalse();
        assertThat(ScoreTableSummaryPolicy.isSummaryRow("合计\n得分", "满分\n（100分）")).isTrue();
    }

    @Test
    void summaryRecognitionSurvivesMisalignedSummaryCells() {
        CanonicalTableGrid grid = grid(List.of(
                cell(0, 0, "编号"), cell(0, 1, "评分因素"), cell(0, 2, "评分标准"),
                cell(1, 0, "合计"), cell(1, 1, "12"), cell(1, 2, "100")
        ), 2);

        assertThat(ScoreTableSummaryPolicy.extractDeclaredTotals(grid, mapping()))
                .extracting(ScoreTableSummaryPolicy.DeclaredTotalEvidence::value)
                .containsExactly(new BigDecimal("100.00"));
        assertThat(ScoreTableSummaryPolicy.isSummaryRow(
                grid.cellsInRow(1))).isTrue();
    }

    @Test
    void standaloneMaximumLabelDoesNotMatchItemMaximumLanguage() {
        assertThat(ScoreTableSummaryPolicy.isSummaryLabel("满\n分")).isTrue();
        assertThat(ScoreTableSummaryPolicy.isSummaryLabel(null)).isFalse();
        assertThat(ScoreTableSummaryPolicy.isSummaryLabel("本项满分10分")).isFalse();
        assertThat(ScoreTableSummaryPolicy.isSummaryRow("1", "综合能力（满分10分）")).isFalse();
        var grid = grid(List.of(cell(0, 0, "序号"), cell(0, 1, "评分因素"), cell(0, 2, "评分标准"),
                cell(1, 0, "1"), cell(1, 1, "综合能力"), cell(1, 2, "本项满分10分")), 2);
        assertThat(ScoreTableSummaryPolicy.isSummaryRow(grid.cellsInRow(1))).isFalse();
        assertThat(ScoreTableSummaryPolicy.extractDeclaredTotals(grid, mapping())).isEmpty();
    }

    private static TableColumnMapping mapping() {
        return new TableColumnMapping(0, -1, 1, 2, -1, 1, false);
    }

    private static CanonicalTableGrid grid(
            List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells, int rows) {
        return CanonicalTableGridFactory.fromSidecarTable(new DocumentArtifact.CanonicalTable(
                "p24-t0007", 24, "综合评分法", rows, 3, List.of(), cells, true));
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(
                "p24-t0007-r" + row + "-c" + column,
                row, column, text, text.replaceAll("\\s+", " ").trim(),
                1, 1, false, "pdf:page=24;table=7;row=" + row + ";col=" + column);
    }
}
