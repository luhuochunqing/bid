package com.xiyu.bid.scoreparse.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreParseDecision;
import com.xiyu.bid.scoreparse.domain.ScoreTypeClassificationPolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** Synthetic source-driven replay through the production extraction and source-verification path. */
public final class SourceBoundaryGoldenReplay {
    private SourceBoundaryGoldenReplay() {}

    public static void verify(String text, JsonNode expected) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[] lines = text.split("\\R", -1);
        int row = 0;
        String section = "";
        for (int line = 0; line < lines.length; line++) {
            if (!lines[line].startsWith("| ") || lines[line].contains("---")) continue;
            if (section.isBlank()) section = OriginalScoringContext.textScopeAt(text, line).title();
            String[] columns = lines[line].split("\\|", -1);
            for (int col = 0; col < 4; col++) {
                String raw = columns[col + 1].strip();
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r" + row + "-c" + col,
                        row, col, raw, raw, 1, 1, false, "pdf:page=3", 3, "t-r" + row));
            }
            row++;
        }
        var table = new DocumentArtifact.CanonicalTable("t", 3, section, row, 4, List.of(), cells, true, "PDF");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "2.2"),
                List.of(table), List.of());
        assertThat(OriginalScoringContext.schemes(artifact, text)).extracting(OriginalScoringContext.SchemeEvidence::title)
                .containsExactly(expected.path("schemeTitle").asText());
        var result = StructuredArtifactScoreExtraction.extract(artifact, "tender.pdf", text);
        assertThat(result.completeness().decision()).as(result.completeness().defects().toString())
                .isEqualTo(ScoreParseDecision.AUTO_APPROVED);
        assertThat(result.originalVerified()).isTrue();
        assertThat(result.facts()).hasSize(expected.path("expectedFactCount").asInt());
        assertThat(result.completeness().leafWeightSum()).isEqualByComparingTo(expected.path("declaredTotal").decimalValue());
        var classifier = new ScoreTypeClassificationPolicy();
        for (var item : expected.path("items")) {
            var fact = result.facts().stream().filter(f -> f.dimension().equals(item.path("dimension").asText()))
                    .findFirst().orElseThrow();
            assertThat(fact.weight()).isEqualByComparingTo(item.path("weight").decimalValue());
            assertThat(fact.sourceText()).isNotBlank().doesNotContain("合计", "格式自拟");
            assertThat(classifier.classify(fact.sourceText())).isEqualTo(item.path("scoreType").asText());
        }
        String material = expected.path("rejectedMaterialQuote").asText();
        assertThat(material).isNotBlank();
        var claim = new MultimodalScoreFact(section, "material", "", "技术", BigDecimal.ONE,
                "模型规则", "SUBJECTIVE", 3, "", List.of(), material, .9);
        assertThat(TextPrimaryEvidenceBinder.bind(List.of(claim), artifact, text).getFirst().sourceCellIds()).isEmpty();
    }
}
