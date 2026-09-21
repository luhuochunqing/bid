package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreEvidencePolicyTest {
    @Test void bodyTotalOutsideTableSupportsEightyPointPlan() {
        var source = artifact("评分办法", List.of(
                cell("n1", 0, 0, "商务服务"), cell("r1", 0, 1, "售后保障最高得30分"), cell("w1", 0, 2, "30分"),
                cell("n2", 1, 0, "技术方案"), cell("r2", 1, 1, "技术方案最高得50分"), cell("w2", 1, 2, "50分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "本项目评分总分：80分。",
                List.of(fact("1", "商务服务", "30", "售后保障最高得30分", "r1"),
                        fact("2", "技术方案", "50", "技术方案最高得50分", "r2")), new BigDecimal("80"));
        assertThat(report.publishable()).isTrue();
        assertThat(report.total().value()).isEqualByComparingTo("80");
        assertThat(report.total().source()).isEqualTo("TENDER_TEXT");
    }

    @Test void duplicateWholeTotalAndCategorySubtotalsAreNotConflicts() {
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分100分，其中商务部分满分10分，技术部分满分90分"),
                cell("n1", 1, 0, "销售额"), cell("r1", 1, 1, "销售额达到1亿元得10分"), cell("w1", 1, 2, "10分"),
                cell("n2", 2, 0, "技术方案"), cell("r2", 2, 1, "技术方案最高得90分"), cell("w2", 2, 2, "90分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source,
                "本项目评分总分100分。\n本项目评分总分100.0分。其他标包评分总分200分。",
                List.of(fact("1", "销售额", "10", "销售额达到1亿元得10分", "r1"),
                        fact("2", "技术方案", "90", "技术方案最高得90分", "r2")), new BigDecimal("100"));
        assertThat(report.publishable()).isTrue();
        assertThat(report.total().value()).isEqualByComparingTo("100");
    }

    @Test void conflictingOrMissingOriginalTotalCannotPublish() {
        var cells = List.of(cell("n1", 0, 0, "销售额"), cell("r1", 0, 1, "销售额得100分"),
                cell("w1", 0, 2, "100分"));
        var fact = fact("1", "销售额", "100", "销售额得100分", "r1");
        var missing = OriginalScoreEvidencePolicy.verifyModel(artifact("评分办法", cells), "",
                List.of(fact), new BigDecimal("100"));
        assertThat(missing.publishable()).isFalse();
        assertThat(missing.total().value()).isNull();
        assertThat(missing.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_TOTAL_UNKNOWN");

        var conflicting = OriginalScoreEvidencePolicy.verifyModel(artifact("评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分100分"),
                cell("n1", 1, 0, "销售额"), cell("r1", 1, 1, "销售额得100分"), cell("w1", 1, 2, "100分"))),
                "本项目评分总分80分", List.of(fact), new BigDecimal("100"));
        assertThat(conflicting.publishable()).isFalse();
        assertThat(conflicting.total().value()).isNull();
        assertThat(conflicting.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ORIGINAL_TOTAL_CONFLICT");
    }

    @Test void unknownOriginalTotalCanBeProjectedWithoutInventingHundred() throws Exception {
        var snapshot = new ScoreCompletenessPolicy.ScoreCompletenessResult(false,
                ScoreParseDecision.REVIEW_REQUIRED, "ORIGINAL_TOTAL_UNKNOWN", null,
                ScoreCompletenessPolicy.DeclaredTotalSource.UNKNOWN, new BigDecimal("100"),
                1, 0, 0, List.of("ORIGINAL_TOTAL_UNKNOWN"), List.of(), List.of()).toReviewSnapshot();
        assertThat(snapshot.get("declaredTotalScore")).isNull();
        assertThat(snapshot.get("discrepancy")).isNull();
        assertThat(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(snapshot))
                .contains("\"declaredTotalScore\":null");
    }

    @Test void sourceWeightUsesItemMaximumNotIncrementOrAmountThreshold() {
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分20分"),
                cell("n1", 1, 0, "证书"), cell("r1", 1, 1, "每份证书得2分，最高10分"), cell("w1", 1, 2, "10分"),
                cell("n2", 2, 0, "销售额"), cell("r2", 2, 1, "合同金额达到100万元得10分"), cell("w2", 2, 2, "10.0分")));
        var valid = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "证书", "10.0", "每份证书得2分，最高10分", "r1"),
                        fact("2", "销售额", "10", "合同金额达到100万元得10分", "r2")), new BigDecimal("20"));
        assertThat(valid.publishable()).isTrue();

        var otherItem = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "销售额", "10", "每份证书得2分，最高10分", "r1")), new BigDecimal("20"));
        assertThat(otherItem.publishable()).isFalse();
        assertThat(otherItem.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_SOURCE_SCOPE_MISMATCH");
    }

    @Test void perCertificateIncrementWithoutCapIsNotItemMaximum() {
        var source = artifact("本项目评分办法", List.of(
                cell("total", 0, 0, "本项目评分总分2分"),
                cell("n1", 1, 0, "证书"), cell("r1", 1, 1, "每份证书得2分")));
        var report = OriginalScoreEvidencePolicy.verifyModel(source, "",
                List.of(fact("1", "证书", "2", "每份证书得2分", "r1")), new BigDecimal("2"));
        assertThat(report.publishable()).isFalse();
        assertThat(report.problems()).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }

    private static MultimodalScoreFact fact(String code, String name, String weight, String rule, String cellId) {
        return new MultimodalScoreFact("本项目评分办法", code, "", name, new BigDecimal(weight), rule,
                "OBJECTIVE", 3, "t1", List.of(cellId), rule, 0.9);
    }

    private static DocumentArtifact artifact(String section,
                                             List<DocumentArtifact.CanonicalTable.CanonicalCellDto> cells) {
        return new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t1", 3, section, 3, 3,
                        List.of(), cells, true)), List.of());
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(
            String id, int row, int column, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, text, text,
                1, 1, false, "");
    }
}
