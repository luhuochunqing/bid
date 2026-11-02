package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreWeightBoundaryRegressionTest {
    @ParameterizedTest
    @CsvSource({"false,0", "true,0", "false,1", "true,1", "false,2", "true,2"})
    void rowOwnershipPreventsCompensatingWeightSwaps(boolean swapped, int quoteKind) {
        String city = "配送服务城市配送最高得28分。";
        String rural = "配送服务郊区配送最高得12分。";
        var artifact = artifact(table("t", new String[][] {
                {"序号", "评审因素", "评分标准", "分值"},
                {"1", "配送服务", city, "28"},
                {"2", "配送服务", rural, "12"},
                {"3", "报价评分", "报价评分最高得60分。", "60"}}));
        var facts = List.of(
                model("1", "配送服务", swapped ? "12" : "28", quoteKind == 1 ? "配送服务" : quoteKind == 2 ? "配送服务 " + city : city,
                        List.of("t-r1-c1", "t-r1-c2", swapped ? "t-r2-c3" : "t-r1-c3")),
                model("2", "配送服务", swapped ? "28" : "12", quoteKind == 1 ? "配送服务" : quoteKind == 2 ? "配送服务 " + rural : rural,
                        List.of("t-r2-c1", "t-r2-c2", swapped ? "t-r1-c3" : "t-r2-c3")),
                model("3", "报价评分", "60", "报价评分最高得60分。", List.of("t-r3-c2", "t-r3-c3")));
        var result = new MultimodalScoreFactValidator().validate(facts, artifact,
                "本项目评分总分100分。", new BigDecimal("100"));
        assertThat(result.leafTotal()).isEqualByComparingTo("100");
        assertThat(result.autoApprovable()).as(result.violations().toString()).isEqualTo(!swapped);
        if (swapped) {
            assertThat(result.violations().toString()).contains("ITEM_WEIGHT_SOURCE_MISMATCH");
        } else {
            assertThat(result.violations()).isEmpty();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void explicitWeightEvidenceCannotReplaceTheDeterministicSourceRow(boolean swapped) {
        var artifact = artifact(table("t", new String[][] {
                {"序号", "评审因素", "评分标准", "分值"},
                {"1", "配送服务", "配送服务城市配送方案。", "28"},
                {"2", "配送服务", "配送服务郊区配送方案。", "12"},
                {"3", "报价评分", "报价评分最高得60分。", "60"}}));
        var facts = List.of(
                withWeightEvidence("1", "配送服务", swapped ? "12" : "28", "配送服务城市配送方案。",
                        "t-r1-c2", swapped ? "t-r2-c3" : "t-r1-c3"),
                withWeightEvidence("2", "配送服务", swapped ? "28" : "12", "配送服务郊区配送方案。",
                        "t-r2-c2", swapped ? "t-r1-c3" : "t-r2-c3"),
                withWeightEvidence("3", "报价评分", "60", "报价评分最高得60分。", "t-r3-c2", "t-r3-c3"));
        assertThat(facts.stream().map(ScoreFact::weight).reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100");
        var result = OriginalScoreEvidencePolicy.verifyFacts(artifact, "本项目评分总分100分。", facts);
        if (swapped) {
            assertThat(result.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                    .contains("ITEM_WEIGHT_SOURCE_MISMATCH");
        } else {
            assertThat(result.problems()).isEmpty();
            assertThat(result.qualifications()).hasSize(3).allMatch(q -> q.candidateEligible());
        }
    }

    private static ScoreFact withWeightEvidence(String code, String dimension, String weight,
                                                String text, String sourceCell, String weightCell) {
        return new ScoreFact("f" + code, "t", List.of(sourceCell), text, code, dimension, new BigDecimal(weight),
                new ScoreEvidence(ScoreEvidence.Kind.EXPLICIT_WEIGHT_COLUMN, weight, weightCell,
                        0, weight.length(), ""), null, "", "STRUCTURED_CODE", .9, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    @ParameterizedTest
    @ValueSource(strings = {"9.2", "1.2"})
    void deterministicParentIdentitySurvivesDuplicateDisplayCodes(String extraCode) {
        String rule = "配送服务满分40分。\n1、城市配送（28分）：城市配送最高得28分。\n"
                + "2、郊区配送（12分）：郊区配送最高得12分。";
        var artifact = artifact(
                table("t", new String[][] {{"序号", "评审因素", "评分标准", "分值"},
                        {"1", "配送服务（40分）", rule, "40"}}),
                table("u", new String[][] {{"序号", "评审因素", "评分标准", "分值"},
                        {extraCode, "仓储服务（40分）", "仓储服务最高得40分。", "40"}}));
        var facts = List.of(
                deterministic("parent-A", "t", "t-r1-c1", "配送服务（40分）", "1", "配送服务", "40", ""),
                deterministic("city-A", "t", "t-r1-c2", "1、城市配送（28分）", "1.1", "配送服务", "28", "parent-A"),
                deterministic("sub-A", "t", "t-r1-c2", "2、郊区配送（12分）", "1.2", "配送服务", "12", "parent-A"),
                deterministic("detail-A", "t", "t-r1-c2", "郊区配送最高得12分。", "1.2.1", "配送服务", "12", "sub-A"),
                deterministic("other-B", "u", "u-r1-c1", "仓储服务（40分）", extraCode, "仓储服务", "40", ""));
        var result = OriginalScoreEvidencePolicy.verifyFacts(artifact, "本项目评分总分80分。", facts);
        assertThat(result.problems()).isEmpty();
        assertThat(result.qualifications()).hasSize(5).allMatch(q -> q.candidateEligible());
    }

    @ParameterizedTest
    @CsvSource({"十,十一", "10,11", "20,21", "100,101"})
    void multiDigitExclusiveTiersHaveTheSameMaximumAsChineseOrdinals(String first, String second) {
        String rule = "方案择一评分：第" + first + "档最高得5分，第" + second + "档最高得3分。";
        var artifact = artifact(table("t", new String[][] {
                {"序号", "评审因素", "评分标准", "分值"},
                {"1", "方案", rule, "5"}, {"2", "报价评分", "报价评分最高得95分。", "95"}}));
        var facts = List.of(model("1", "方案", "5", rule, List.of("t-r1-c1", "t-r1-c2", "t-r1-c3")),
                model("2", "报价评分", "95", "报价评分最高得95分。", List.of("t-r2-c1", "t-r2-c2", "t-r2-c3")));
        var result = new MultimodalScoreFactValidator().validate(facts, artifact,
                "本项目评分总分100分。", new BigDecimal("100"));
        assertThat(result.autoApprovable()).as(result.violations().toString()).isTrue();
        assertThat(result.violations()).isEmpty();
        assertThat(result.leafTotal()).isEqualByComparingTo("100");
    }

    private static MultimodalScoreFact model(String code, String dimension, String weight, String quote, List<String> ids) {
        return new MultimodalScoreFact("本项目评分办法", code, "", dimension, new BigDecimal(weight),
                quote, "OBJECTIVE", 1, "t", ids, quote, .9);
    }

    private static ScoreFact deterministic(String id, String table, String cell, String text, String code,
                                           String dimension, String weight, String parent) {
        return new ScoreFact(id, table, table.equals("u") ? List.of(cell, "u-r1-c2", "u-r1-c3") : List.of(cell),
                text, code, dimension, new BigDecimal(weight), null, null, parent,
                "STRUCTURED_CODE", .9, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }

    private static DocumentArtifact artifact(DocumentArtifact.CanonicalTable... tables) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "sha", "PDF", "2.2"),
                List.of(tables), List.of());
    }

    private static DocumentArtifact.CanonicalTable table(String id, String[][] rows) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        for (int row = 0; row < rows.length; row++) {
            for (int column = 0; column < rows[row].length; column++) {
                String raw = rows[row][column];
                cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto(id + "-r" + row + "-c" + column,
                        row, column, raw, raw, 1, 1, false, "fixture:" + id, 1, id + "-r" + row));
            }
        }
        return new DocumentArtifact.CanonicalTable(id, 1, "本项目评分办法", rows.length, 4, List.of(), cells, true);
    }
}
