package com.xiyu.bid.scoreparse.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

public class Project1359ScoringContractTest {
    @Test void real1359MaximumColumnsDoNotConflictWithMinimumTotals() throws Exception {
        var context = realContext();
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-0002").value()).isEqualByComparingTo("100");
        assertThat(context.subtotals().get("t-0003").value()).isEqualByComparingTo("100");
    }

    @Test void reversedMinimumMaximumColumnsStillUseMaximum() {
        var context = context(List.of("评审项目", "评审内容", "最高分", "最低分"),
                List.of("合计", "", "100", "38"));
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void unidentifiedMultipleNumbersAreNotGuessedAsMaximum() {
        var context = context(List.of("评审项目", "评审内容", "数值甲", "数值乙"),
                List.of("合计", "", "12", "100"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void explicitTotalsInUnidentifiedColumnsCannotDisappear() {
        var context = context(List.of("评审项目", "评审内容", "数值甲", "数值乙"),
                List.of("合计", "", "总分100分", "满分90分"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void emptyMaximumCellDoesNotSilentlyDiscardTableSubtotal() {
        var context = context(List.of("评审项目", "评审内容", "最低分", "最高分"),
                List.of("合计", "", "38", ""));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void singleNumberUnderUnknownHeaderIsNotColumnEvidence() {
        var context = context(List.of("评审项目", "评审内容", "数值"), List.of("合计", "", "100"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void precedingBannerDoesNotHijackRealScoreHeader() {
        var table = table("t-test", "本项目评分办法 / 技术评审细则",
                List.of(List.of("满分", "", "", ""), List.of("序号", "评审项目", "评审内容", "分值"),
                        List.of("1", "方案", "方案完整得100分", "100"), List.of("合计", "", "", "100")));
        var context = OriginalScoringContext.resolve(artifact(List.of(table)), "# 本项目评分办法\n总分100分。");
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void onlyMinimumColumnCannotBecomeMaximum() {
        var context = context(List.of("评审项目", "评审内容", "最低分"), List.of("合计", "", "38"));
        assertThat(context.subtotals()).isEmpty();
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_COLUMN_UNRESOLVED");
    }

    @Test void explicitTotalWordingInMinimumCellCannotOverrideMaximumColumn() {
        var context = context(List.of("评审项目", "评审内容", "最低分", "最高分"),
                List.of("合计", "", "总分12分", "100"));
        assertThat(context.problems()).isEmpty();
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
    }

    @Test void parenthesizedExplicitMaximumUsesTheSameGrammarAsContext() {
        var context = context(List.of("评审项目", "评审内容", "满分"), List.of("合计", "", "满分（100分）"));
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    @Test void explicitMaximumWithWeightAnnotationIsNotUnknown() {
        var context = context(List.of("评审项目", "评审内容", "最高分"), List.of("合计", "", "满分100分（权重20%）"));
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    @Test void explicitMaximumAndScoreHeaderAliasesRemainCompatible() {
        for (String header : List.of("最高分数", "最高分值", "最高得分", "满分（分）", "评分分值")) {
            var context = context(List.of("评审项目", "评审内容", header), List.of("合计", "", "100"));
            assertThat(context.problems()).as(header).isEmpty();
            assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        }
    }

    @Test void singleExplicitScoreColumnRemainsCompatible() {
        var context = context(List.of("评审项目", "评审内容", "分值"), List.of("合计", "", "100"));
        assertThat(context.subtotals().get("t-test").value()).isEqualByComparingTo("100");
        assertThat(context.problems()).isEmpty();
    }

    @Test void differentTotalsInSameMaximumColumnStillConflict() {
        var headers = List.of("评审项目", "评审内容", "最低分", "最高分");
        var table = table("t-test", "本项目评分办法 / 技术评审细则",
                List.of(headers, List.of("方案", "方案完整得100分", "0", "100"),
                        List.of("合计", "", "38", "100"), List.of("总计", "", "38", "90")));
        var context = OriginalScoringContext.resolve(artifact(List.of(table)), "# 本项目评分办法\n总分100分。");
        assertThat(context.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("TABLE_SUBTOTAL_CONFLICT");
    }

    public static OriginalScoringContext realContext() throws Exception {
        JsonNode raw;
        String text;
        try (var in = Project1359ScoringContractTest.class.getResourceAsStream("/scoreparse-golden/1359_tables_v1.json")) {
            raw = new ObjectMapper().readTree(in);
        }
        try (var in = Project1359ScoringContractTest.class.getResourceAsStream("/scoreparse-golden/1359_sidecar.md")) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        List<DocumentArtifact.CanonicalTable> tables = new ArrayList<>();
        for (int index : List.of(2, 3)) {
            var rows = new ArrayList<List<String>>();
            for (JsonNode row : raw.path("tables").get(index).path("rows")) {
                var values = new ArrayList<String>();
                for (JsonNode cell : row) values.add(cell.path("text").asText());
                rows.add(values);
            }
            tables.add(table("t-000" + index, index == 2 ? "商务评审细则" : "技术评审细则", rows));
        }
        return OriginalScoringContext.resolve(artifact(tables), text);
    }

    private static OriginalScoringContext context(List<String> headers, List<String> summary) {
        var row = new ArrayList<String>();
        row.add("方案"); row.add("方案完整得100分");
        while (row.size() < headers.size()) row.add("0");
        row.set(headers.size() - 1, "100");
        return OriginalScoringContext.resolve(artifact(List.of(table("t-test",
                "本项目评分办法 / 技术评审细则", List.of(headers, row, summary)))), "# 本项目评分办法\n总分100分。");
    }

    private static DocumentArtifact artifact(List<DocumentArtifact.CanonicalTable> tables) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("1359", "fixture-1359", "DOCX", "2.2"), tables, List.of());
    }

    private static DocumentArtifact.CanonicalTable table(String id, String section, List<List<String>> rows) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        for (int r = 0; r < rows.size(); r++) for (int c = 0; c < rows.get(r).size(); c++) {
            String text = rows.get(r).get(c);
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(id + "-r" + r + "-c" + c,
                    r, c, text, text, 1, 1, false, "fixture:" + id));
        }
        return new DocumentArtifact.CanonicalTable(id, 1, section, rows.size(), rows.get(0).size(), List.of(), cells, true);
    }
}
