package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Portable source contract; no live model, customer PDF, or OCR availability required. */
public class Project43SourceIdentityRegressionTest {
    @Test void fullHandlerPreservesElevenRulesWithoutCountingThreeSourceAliases() throws Exception {
        String name = "/scoreparse-golden/case-source-alias-double-count";
        try (var markdown = getClass().getResourceAsStream(name + ".md");
             var expected = getClass().getResourceAsStream(name + ".expected.json")) {
            verifyGolden(new String(markdown.readAllBytes(), StandardCharsets.UTF_8), new ObjectMapper().readTree(expected));
        }
    }

    public static void verifyGolden(String text, JsonNode expected) {
        var cells = new ArrayList<CanonicalCellDto>();
        var facts = new ArrayList<MultimodalScoreFact>();
        var rows = text.lines().filter(line -> line.startsWith("|") && !line.startsWith("| ---")).toList();
        for (int row = 0; row < rows.size(); row++) {
            String[] values = rows.get(row).split("\\|");
            if (row == 1) {
                int line = text.lines().toList().indexOf(rows.get(row)) + 1;
                facts.add(new MultimodalScoreFact("第三章 评审标准", "1", "", values[2].strip(),
                        new BigDecimal(values[3].strip()), values[4].strip(), "OBJECTIVE", 24, "",
                        List.of("text:line[" + line + "]"), values[4].strip(), .9, ChildAggregation.SUM));
                continue; // Price is paragraph-backed, as in the production document; tables alone total 70.
            }
            int sourceRow = row == 0 ? 0 : row - 1;
            for (int col = 0; col < 4; col++) {
                String value = values[col + 1].strip();
                cells.add(new CanonicalCellDto("t-r" + sourceRow + "-c" + col, sourceRow, col, value, value,
                        1, 1, false, "pdf:page=24", 24, "t-r" + sourceRow));
            }
            if (row > 0) facts.add(new MultimodalScoreFact("第三章 评审标准", values[1].strip(), "", values[2].strip(),
                    new BigDecimal(values[3].strip()), values[4].strip(), "OBJECTIVE", 24, "t",
                    List.of("t-r" + sourceRow + "-c3"), values[4].strip(), .9, ChildAggregation.SUM));
        }
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "2.2"),
                List.of(new CanonicalTable("t", 24, "第三章 评审标准", rows.size() - 1, 4, List.of(), cells, true, "PDF")), List.of());
        var duplicated = new ArrayList<>(facts);
        for (int index : List.of(5, 6, 7)) {
            var fact = facts.get(index);
            duplicated.add(new MultimodalScoreFact(fact.sectionTitle(), "alias-" + fact.code(), "", fact.dimension(),
                    fact.weight(), "", fact.scoreType(), fact.pageNumber(), fact.tableId(), fact.sourceCellIds(),
                    fact.quotedText(), .95, fact.childAggregation()));
        }
        assertThat(duplicated.stream().map(MultimodalScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("119");
        var result = MultimodalStructuredExtractionHandler.run("synthetic.pdf", text, artifact,
                OriginalScoringContext.resolve(artifact, text), null, () -> {},
                new MultimodalScoreFactExtractor.ExtractionResult(new BigDecimal("100"), duplicated, "recorded-response", 0),
                true, true, true);
        var leaves = ScoreUnitPolicy.effectiveFacts(result.facts());
        assertThat(leaves).hasSize(expected.path("expectedCount").asInt());
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo(expected.path("expectedLeafTotal").decimalValue());
        for (var item : expected.path("items")) {
            var matching = leaves.stream().filter(f -> f.dimension().equals(item.path("dimension").asText())).toList();
            assertThat(matching).hasSize(1);
            assertThat(matching.getFirst().weight()).isEqualByComparingTo(item.path("weight").decimalValue());
            assertThat(matching.getFirst().sourceText()).contains("不得分");
            assertThat(matching.getFirst().sourceCellIds()).isNotEmpty();
        }
    }
}
