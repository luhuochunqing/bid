package com.xiyu.bid.scoreparse.application;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.*;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** Registered synthetic Golden quotes also exercise the production binding/adoption path. */
class MarkdownRowQuotationGoldenReplayTest {
    @Test void registeredInnerRowQuotesRestoreOriginalRulesBeforeOneHundredPointAdoption() throws Exception {
        var resource = getClass().getResourceAsStream("/scoreparse-golden/case-markdown-inner-row.expected.json");
        var expected = new ObjectMapper().readTree(resource);
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        for (int col = 0; col < 3; col++) cells.add(cell(0, col,
                List.of("评审项目", "分值", "评审标准").get(col)));
        var facts = new ArrayList<MultimodalScoreFact>();
        int row = 1;
        for (var item : expected.path("items")) {
            cells.add(cell(row, 0, item.path("dim").asText()));
            cells.add(cell(row, 1, item.path("weight").asText()));
            cells.add(cell(row, 2, item.path("sourceText").asText()));
            facts.add(new MultimodalScoreFact("第三章 评审办法", item.path("code").asText(), "", "模型分类",
                    item.path("weight").decimalValue(), "模型摘要", "SUBJECTIVE", null, "", List.of(),
                    item.path("modelQuote").asText(), .9));
            row++;
        }
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "DOCX", "v2"),
                List.of(new DocumentArtifact.CanonicalTable("t", null, "第三章 评审办法", row, 3,
                        List.of(), cells, true)), List.of());
        var bound = TextPrimaryEvidenceBinder.bind(facts, artifact, "");
        var validation = new MultimodalScoreFactValidator().validate(bound, artifact, new BigDecimal("100"));
        var mapped = bound.stream().map(f -> TextPrimaryScoreFactMapper.map(f, artifact, validation)).toList();
        for (int i = 0; i < mapped.size(); i++) {
            var item = expected.path("items").get(i);
            assertThat(mapped.get(i).sourceText()).isEqualTo(item.path("dim").asText() + "\n"
                    + item.path("weight").asText() + "\n" + item.path("sourceText").asText());
            assertThat(mapped.get(i).dimension()).isEqualTo(item.path("dim").asText());
        }
        var adopted = new ScoreReviewAdoptionPolicy().adopt(mapped.stream().map(f ->
                new ScoreReviewAdoptionPolicy.CandidateInput(f.factId(), f.code(), f.dimension(), f.weight(),
                        f.sourceText(), f.parentFactId())).toList(), Set.of(), Map.of());
        assertThat(adopted.success()).isTrue();
        assertThat(adopted.candidates()).hasSize(3);
        assertThat(adopted.totalWeight()).isEqualByComparingTo(expected.path("expectTotal").decimalValue());
    }
    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(int row, int col, String raw) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r" + row + "-c" + col, row, col,
                raw, raw, 1, 1, false, "/w:tbl[1]/w:tr[" + (row + 1) + "]/w:tc[" + (col + 1) + "]",
                null, "t-r" + row);
    }
}
