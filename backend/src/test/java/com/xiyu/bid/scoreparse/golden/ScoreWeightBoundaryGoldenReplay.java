package com.xiyu.bid.scoreparse.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFact;
import com.xiyu.bid.scoreparse.domain.MultimodalScoreFactValidator;
import com.xiyu.bid.scoreparse.domain.OriginalScoreEvidencePolicy;
import com.xiyu.bid.scoreparse.domain.ScoreEvidence;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

/** File-driven source/claim separation for the three reviewed conversion defects. */
final class ScoreWeightBoundaryGoldenReplay {
    private ScoreWeightBoundaryGoldenReplay() {}

    static void verify(String markdown, JsonNode expected) {
        requireArray(expected, "scenarios");
        assertThat(expected.path("expectedChecks").asInt()).isPositive();
        int checks = 0;
        var tables = tables(markdown);
        for (var scenario : expected.path("scenarios")) {
            requireArray(scenario, "tables");
            requireArray(scenario, "variants");
            requireArray(scenario, "modes");
            var selected = tables.stream().filter(t -> strings(scenario.path("tables")).contains(t.tableId())).toList();
            assertThat(selected).hasSize(scenario.path("tables").size());
            var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "boundaries", "PDF", "2.2"),
                    selected, List.of());
            for (var variant : scenario.path("variants")) {
                requireArray(variant, "facts");
                assertThat(variant.path("accepted").isBoolean()).isTrue();
                if (!variant.path("accepted").asBoolean()) assertThat(variant.path("problem").asText()).isNotBlank();
                for (var mode : scenario.path("modes")) {
                    if (mode.asText().equals("model")) verifyModel(artifact, variant, scenario);
                    else if (mode.asText().equals("facts")) verifyFacts(artifact, variant, scenario);
                    else throw new IllegalArgumentException("Unsupported fixture mode: " + mode.asText());
                    checks++;
                }
            }
        }
        assertThat(checks).isEqualTo(expected.path("expectedChecks").asInt());
    }

    private static void requireArray(JsonNode node, String field) {
        assertThat(node.path(field).isArray()).as(field + " must be an array").isTrue();
        assertThat(node.path(field).size()).as(field + " must not be empty").isPositive();
    }

    private static void verifyModel(DocumentArtifact artifact, JsonNode variant, JsonNode scenario) {
        var facts = new ArrayList<MultimodalScoreFact>();
        for (var f : variant.path("facts")) {
            var ids = new ArrayList<>(strings(f.path("cells")));
            if (f.has("weightCell")) ids.add(f.path("weightCell").asText());
            facts.add(new MultimodalScoreFact("本项目评分办法", f.path("code").asText(), "", f.path("dimension").asText(),
                    f.path("weight").decimalValue(), f.path("quote").asText(), "OBJECTIVE", 1,
                    f.path("table").asText(), ids, f.path("quote").asText(), .9));
        }
        var result = new MultimodalScoreFactValidator().validate(facts, artifact,
                totalText(scenario), scenario.path("total").decimalValue());
        assertThat(result.leafTotal()).as(variant.path("name").asText()).isEqualByComparingTo(scenario.path("total").decimalValue());
        assertThat(result.autoApprovable()).as(result.violations().toString()).isEqualTo(variant.path("accepted").asBoolean());
        if (variant.path("accepted").asBoolean()) assertThat(result.violations()).isEmpty();
        else assertThat(result.violations().toString()).contains(variant.path("problem").asText());
    }

    private static void verifyFacts(DocumentArtifact artifact, JsonNode variant, JsonNode scenario) {
        var facts = new ArrayList<ScoreFact>();
        for (var f : variant.path("facts")) {
            String weight = f.path("weight").asText();
            var evidence = !f.has("weightCell") ? null : new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN,
                    weight, f.path("weightCell").asText(), 0, weight.length(), "");
            facts.add(new ScoreFact(f.path("id").asText(), f.path("table").asText(), strings(f.path("cells")),
                    f.path("quote").asText(), f.path("code").asText(), f.path("dimension").asText(),
                    f.path("weight").decimalValue(), evidence, null, f.path("parentId").asText(),
                    "STRUCTURED_CODE", .9, ScoreFact.ValidationStatus.AUTO_APPROVED));
        }
        var result = OriginalScoreEvidencePolicy.verifyFacts(artifact, totalText(scenario), facts);
        if (variant.path("accepted").asBoolean()) {
            assertThat(result.problems()).as(variant.path("name").asText()).isEmpty();
            assertThat(result.qualifications()).hasSize(facts.size()).allMatch(q -> q.candidateEligible());
        } else {
            assertThat(result.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                    .contains(variant.path("problem").asText());
        }
    }

    private static String totalText(JsonNode scenario) {
        return "本项目评分总分" + scenario.path("total").asText() + "分。";
    }

    private static List<String> strings(JsonNode values) {
        var result = new ArrayList<String>();
        values.forEach(v -> result.add(v.asText()));
        return List.copyOf(result);
    }

    private static List<DocumentArtifact.CanonicalTable> tables(String markdown) {
        var result = new ArrayList<DocumentArtifact.CanonicalTable>();
        String id = "";
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        int row = 0;
        for (String line : (markdown + "\n## END").lines().toList()) {
            if (line.startsWith("## ")) {
                if (!cells.isEmpty()) result.add(new DocumentArtifact.CanonicalTable(id, 1, "本项目评分办法",
                        row, 4, List.of(), List.copyOf(cells), true));
                id = line.substring(3).strip();
                cells.clear();
                row = 0;
            } else if (line.startsWith("|") && !line.matches("[| :\\-]+")) {
                String[] columns = line.split("\\|", -1);
                for (int column = 1; column < columns.length - 1; column++) {
                    String raw = columns[column].strip().replace("<br>", "\n");
                    cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(id + "-r" + row + "-c" + (column - 1),
                            row, column - 1, raw, raw, 1, 1, false, "fixture", 1, id + "-r" + row));
                }
                row++;
            }
        }
        return List.copyOf(result);
    }
}
