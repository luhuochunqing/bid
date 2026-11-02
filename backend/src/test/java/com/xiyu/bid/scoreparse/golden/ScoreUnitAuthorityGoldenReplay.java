package com.xiyu.bid.scoreparse.golden;

// Input: synthetic source table and separate model claims/expected diagnostics.
// Output: positive source eligibility and wrong-child source mismatch assertions.
// Pos: golden replay support; exercises existing evidence verification, no OCR call.
import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

final class ScoreUnitAuthorityGoldenReplay {
    private ScoreUnitAuthorityGoldenReplay() {}

    static void verify(String markdown, JsonNode expected) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        int row = 0;
        for (String line : markdown.lines().filter(s -> s.startsWith("|")).toList()) {
            if (line.matches("[| :\\-]+")) continue;
            String[] values = line.split("\\|", -1);
            for (int column = 1; column < values.length - 1; column++) {
                String raw = values[column].strip().replace("<br>", "\n");
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(id(row,column-1), row,column-1,
                        raw,raw,1,1,false,"fixture:page=1",1,"t-r"+row));
            }
            row++;
        }
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic","unit-scope","PDF","2.2"),
                List.of(new DocumentArtifact.CanonicalTable("t",1,"本项目评分办法",row,4,List.of(),cells,true)),List.of());
        var facts = new ArrayList<MultimodalScoreFact>();
        expected.path("facts").forEach(f -> facts.add(new MultimodalScoreFact("本项目评分办法",
                f.path("code").asText(), f.path("parent").asText(), f.path("dimension").asText(),
                f.path("weight").decimalValue(), f.path("quote").asText(), "OBJECTIVE",1,"t",
                List.of(id(f.path("row").asInt(),f.path("column").asInt())),f.path("quote").asText(),0.9)));
        var valid = OriginalScoreEvidencePolicy.verifyModel(artifact,markdown,facts,expected.path("declaredTotal").decimalValue());
        assertThat(valid.qualifications()).hasSize(expected.path("expectedEligibleFacts").asInt())
                .allMatch(q -> q.candidateEligible());
        assertThat(valid.problems()).isEmpty();
        var wrong = facts.stream().map(f -> f.code().equals(expected.path("wrongChildCode").asText())
                ? new MultimodalScoreFact(f.sectionTitle(),f.code(),f.parentCode(),f.dimension(),
                expected.path("wrongChildWeight").decimalValue(),f.ruleText(),f.scoreType(),f.pageNumber(),f.tableId(),
                f.sourceCellIds(),f.quotedText(),f.confidence(),f.childAggregation(),f.schemeId(),f.scopeStatus(),f.sourceMetadata()) : f).toList();
        var rejected = OriginalScoreEvidencePolicy.verifyModel(artifact,markdown,wrong,expected.path("declaredTotal").decimalValue());
        assertThat(rejected.problems()).anySatisfy(p -> {
            assertThat(p.factId()).isEqualTo(OriginalScoreEvidencePolicy.modelFactId(wrong.get(1)));
            assertThat(p.code()).isEqualTo(expected.path("expectedProblem").asText());
            assertThat((BigDecimal)p.expected()).isEqualByComparingTo(expected.path("expectedSourceWeight").decimalValue());
        });
    }

    private static String id(int row, int column) { return "t-r"+row+"-c"+column; }
}
