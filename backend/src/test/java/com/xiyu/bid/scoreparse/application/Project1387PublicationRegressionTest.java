package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.ChildAggregation;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.Project1387SourceRegressionTest;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact.CanonicalTable.CanonicalCellDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class Project1387PublicationRegressionTest {
    @Test void sourceDerivedFactsReachPublicationWithoutInventedTotalOrScope() throws Exception {
        var artifact = Project1387SourceRegressionTest.fixtureArtifact();
        String text = Project1387SourceRegressionTest.fixtureText();
        var table = artifact.tables().stream().filter(t -> t.tableId().equals("t-0005")).findFirst().orElseThrow();
        List<MultimodalScoreFact> facts = new ArrayList<>();
        String parentCode = "";
        for (int row = 1; row < table.rows(); row++) {
            int rowNumber = row;
            var cells = table.cells().stream().filter(c -> Integer.valueOf(rowNumber).equals(c.row()))
                    .sorted(java.util.Comparator.comparing(CanonicalCellDto::column)).toList();
            String name = cells.get(0).rawText();
            boolean parent = name.equals("商务部分") || name.equals("技术部分");
            String code = "item-" + row;
            if (name.equals("价格部分")) parentCode = "";
            facts.add(new MultimodalScoreFact("第三章 评审办法", code, parent ? "" : parentCode,
                    name, new BigDecimal(cells.get(1).rawText()),
                    cells.get(2).rawText().isBlank() ? name : cells.get(2).rawText(), "SUBJECTIVE", null,
                    "", List.of(), cells.get(2).rawText().isBlank() ? name : cells.get(2).rawText(),
                    0.9, ChildAggregation.SUM));
            if (parent) parentCode = code;
        }
        // Facts are source-derived replay inputs, not a model-response or live-LLM success claim.
        var result = MultimodalStructuredExtractionHandler.run("tender.docx", text, artifact,
                OriginalScoringContext.resolve(artifact, text), null, () -> {},
                new MultimodalScoreFactExtractor.ExtractionResult(null, facts, "source-replay", 0), true, true, true);
        assertThat(result.completeness().decision()).as(result.completeness().defects().toString())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).as("candidates=" + result.candidates().size() + "; facts=" + result.facts()).isTrue();
        assertThat(result.candidates()).hasSize(12);
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo("100");
        assertThat(com.xiyu.bid.scoreparse.domain.ScoreUnitPolicy.effectiveFacts(result.facts())).allSatisfy(fact -> {
            assertThat(fact.sourceText()).isNotBlank();
            assertThat(fact.sourceCellIds()).isNotEmpty();
        });
    }
}
