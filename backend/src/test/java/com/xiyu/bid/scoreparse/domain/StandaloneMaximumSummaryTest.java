// Input: synthetic four-column score table ending with a standalone maximum row
// Output: details counted once and source-anchored declared total
// Pos: scoreparse/domain regression for project 1123; no customer document content
package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.CanonicalTableGridFactory;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.TableColumnMapping;
import com.xiyu.bid.scoreparse.domain.grid.TableGridExtractor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

public class StandaloneMaximumSummaryTest {
    private static final String SCHEME = "第三章 评审办法（综合评估法）";

    @Test void standaloneMaximumDoesNotBecomeAnAdditionalHundredPointFact() {
        var grid = CanonicalTableGridFactory.fromSidecarTable(scoringTable());
        var mapping = new TableColumnMapping(0, -1, 1, 3, 2, 1, false);
        var facts = DeterministicScoreFactExtractor.extract(grid, mapping);
        assertThat(facts).extracting(ScoreFact::code).containsExactly("1", "2");
        assertThat(facts.stream().map(ScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        assertThat(ScoreTableSummaryPolicy.extractDeclaredTotals(grid, mapping))
                .singleElement().satisfies(total -> {
                    assertThat(total.value()).isEqualByComparingTo("100");
                    assertThat(total.sourceCellId()).isEqualTo("score-r3-c2");
                });
    }

    @Test void sourceTotalSurvivesASecondPhysicalQualificationTable() {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        addRow(cells, "qualification", 0, "条款号", "评审因素", "评审标准", "说明");
        addRow(cells, "qualification", 1, "1", "供应商名称", "须与证照一致", "");
        var qualification = new DocumentArtifact.CanonicalTable("qualification", 1, SCHEME,
                2, 4, List.of(), cells, false);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "summary", "pdf", "2.2"),
                List.of(qualification, scoringTable()), List.of());
        var context = OriginalScoringContext.resolve(artifact, "");
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().locator()).isEqualTo("score:score-r3-c2");
    }

    @ParameterizedTest
    @CsvSource({"合计,满分100分", "总计,满分100分", "满分,100"})
    void headerlessSummaryRemainsSourceTotalBesideDetailTable(String label, String value) {
        var original = scoringTable();
        var details = new DocumentArtifact.CanonicalTable("score", 2, SCHEME, 3, 4, List.of(),
                original.cells().stream().filter(c -> c.row() < 3).toList(), true);
        var facts = DeterministicScoreFactExtractor.extract(CanonicalTableGridFactory.fromSidecarTable(details),
                new TableColumnMapping(0, -1, 1, 3, 2, 1, false));
        assertThat(facts).extracting(ScoreFact::code).containsExactly("1", "2");
        assertThat(facts.stream().map(ScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        addRow(cells, "summary", 0, label, value);
        var summary = new DocumentArtifact.CanonicalTable("summary", 3, SCHEME, 1, 2, List.of(), cells, false);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "headerless", "pdf", "2.2"),
                List.of(details, summary), List.of());

        var context = OriginalScoringContext.resolve(artifact, "");

        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.total().source()).isEqualTo("TABLE");
        assertThat(context.total().locator()).isEqualTo("summary:summary-r0-c1");
        assertThat(context.total().quote()).isEqualTo(value);
        assertThat(context.total().problems()).isEmpty();
        assertThat(context.problems()).isEmpty();
    }

    @Test void mergedMaximumLabelAndValueRemainSourceTotalOnly() {
        var original = scoringTable();
        var cells = new ArrayList<>(original.cells().stream().filter(c -> c.row() != 3).toList());
        for (int col = 0; col < 4; col++) {
            boolean origin = col % 2 == 0;
            String value = col == 0 ? "满分" : col == 2 ? "100" : "";
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("score-r3-c" + col,
                    3, col, value, value, 1, origin ? 2 : 1, !origin, "synthetic"));
        }
        var table = new DocumentArtifact.CanonicalTable("score", 2, SCHEME, 4, 4,
                List.of(new DocumentArtifact.CanonicalTable.MergedRegionDto("score-r3-c0", 1, 2),
                        new DocumentArtifact.CanonicalTable.MergedRegionDto("score-r3-c2", 1, 2)), cells, true);
        var grid = CanonicalTableGridFactory.fromSidecarTable(table);
        assertThat(grid.isStructurallyValid()).isTrue();
        assertThat(DeterministicScoreFactExtractor.extract(grid, new TableColumnMapping(0, -1, 1, 3, 2, 1, false)))
                .extracting(ScoreFact::code).containsExactly("1", "2");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "merged", "pdf", "2.2"),
                List.of(table), List.of());
        assertThat(OriginalScoringContext.resolve(artifact, "").total().value()).isEqualByComparingTo("100");
    }

    @Test void maximumWeightColumnHeaderIsNotASummaryRow() {
        var original = scoringTable();
        var cells = original.cells().stream().map(c -> c.row() == 0 && c.column() == 2
                ? new DocumentArtifact.CanonicalTable.CanonicalCellDto(c.cellId(), c.row(), c.column(),
                        "满分", "满分", 1, 1, false, "synthetic") : c).toList();
        var table = new DocumentArtifact.CanonicalTable("score", 2, SCHEME, 4, 4, List.of(), cells, true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "header", "pdf", "2.2"),
                List.of(table), List.of());
        var context = OriginalScoringContext.resolve(artifact, "");
        assertThat(context.total().value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    public static void verifyGolden(String markdown, com.fasterxml.jackson.databind.JsonNode expected) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        var sourceGrid = TableGridExtractor.extract(markdown);
        for (int row = 0; row < sourceGrid.rowCount(); row++) {
            addRow(cells, "golden", row, sourceGrid.row(row).toArray(String[]::new));
        }
        var table = new DocumentArtifact.CanonicalTable("golden", 1, SCHEME, sourceGrid.rowCount(), 4, List.of(), cells, true);
        var grid = CanonicalTableGridFactory.fromSidecarTable(table);
        var facts = DeterministicScoreFactExtractor.extract(grid, new TableColumnMapping(0, -1, 1, 3, 2, 1, false));
        assertThat(facts).hasSize(expected.path("expectedFactCount").asInt());
        for (int i = 0; i < facts.size(); i++) {
            var item = expected.path("items").get(i);
            assertThat(facts.get(i).code()).isEqualTo(item.path("code").asText());
            assertThat(facts.get(i).dimension()).isEqualTo(item.path("dim").asText());
            assertThat(facts.get(i).weight()).isEqualByComparingTo(item.path("weight").decimalValue());
            assertThat(facts.get(i).sourceText()).contains(item.path("detail").asText());
        }
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "golden", "pdf", "2.2"),
                List.of(table), List.of());
        var total = OriginalScoringContext.resolve(artifact, markdown).total();
        assertThat(total.value()).isEqualByComparingTo(expected.path("declaredTotal").decimalValue());
        assertThat(total.locator()).isEqualTo("golden:" + expected.path("expectedTotalCell").asText());
    }

    private static DocumentArtifact.CanonicalTable scoringTable() {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        addRow(cells, "score", 0, "序号", "评分项", "分值", "评分细则");
        addRow(cells, "score", 1, "1", "实施方案", "40", "本项满分40分，方案完整得40分。");
        addRow(cells, "score", 2, "2", "履约能力", "60", "提供有效履约证明得60分。");
        addRow(cells, "score", 3, "满分", "", "100", "");
        return new DocumentArtifact.CanonicalTable("score", 2, SCHEME, 4, 4, List.of(), cells, true);
    }

    private static void addRow(List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells,
                               String tableId, int row, String... values) {
        for (int col = 0; col < values.length; col++) cells.add(
                new DocumentArtifact.CanonicalTable.CanonicalCellDto(tableId + "-r" + row + "-c" + col,
                        row, col, values[col], values[col], 1, 1, false, "synthetic"));
    }
}
