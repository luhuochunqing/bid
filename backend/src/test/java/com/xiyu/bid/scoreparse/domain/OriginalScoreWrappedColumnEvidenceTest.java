package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OriginalScoreWrappedColumnEvidenceTest {
    @Test void wrappedScoreHeaderProvesRowMaximumDespiteSmallerConditionalAndSubscores() {
        assertThat(problems("分\n值", "10", "方案分档：优得3分，良得1分；仅提供宣传材料的，本项最高得0.2分。", "10")).isEmpty();
    }
    @Test void aQuotedMatchingNumberCannotHideAContradictoryMaximum() {
        assertThat(problems("分\n值", "100分", "方案最高得90分，分值列注明100分。", "100"))
                .extracting(OriginalScoreEvidencePolicy.Problem::code).contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }
    @Test void multipleSourceCellsCannotBypassRuleMaximumVerification() {
        for (var ids : List.of(List.of("name", "rule", "value"), List.of("rule")))
            assertThat(problems("分\n值", "100分", "方案最高得90分，分值列注明100分。", "100", ids))
                    .extracting(OriginalScoreEvidencePolicy.Problem::code).contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }
    @Test void multipleSourceCellsRetainVerifiedConditionalMaximum() {
        assertThat(problems("分\n值", "10", "方案分档：优得3分，良得1分；仅提供宣传材料的，本项最高得0.2分。", "10",
                List.of("name", "rule", "value"))).isEmpty();
    }
    @Test void explicitExclusiveTierMaximaUseTheHighestTier() {
        String rule = "方案按优良中差分档评定，择一评分：优秀最高得5分，良好最高得3分，差最高得1分。";
        assertThat(problems("分值", "5", rule, "5", List.of("name", "rule", "value"))).isEmpty();
    }
    @Test void exclusiveTiersDoNotHideAnIndependentWholeItemMaximum() {
        String tiers = "方案按优良中差分档评定，择一评分：优秀最高得5分，良好最高得3分，差最高得1分。";
        for (String maximum : List.of("4", "6"))
            assertThat(problems("分值", "5", "整体方案最高得" + maximum + "分。" + tiers, "5"))
                    .extracting(OriginalScoreEvidencePolicy.Problem::code).contains("ITEM_WEIGHT_SOURCE_UNKNOWN");
    }
    @Test void scopedTierMaximaAreNotMistakenForWholeItemMaxima() {
        String rule = "方案择一评分：优秀本项最高得5分，良好本项最高得3分，差本项最高得1分。";
        assertThat(problems("分值", "5", rule, "5")).isEmpty();
    }
    @Test void unrelatedNumericColumnAndExplicitContradictionDoNotProveWeight() {
        assertThat(problems("报价", "10", "方案分档：优得3分，良得1分。", "10")).isNotEmpty();
        assertThat(problems("分\n值", "10", "方案本项最高得8分。", "10")).isNotEmpty();
        assertThat(problems("分\n值", "10", "本评分标准仅评方案，本项最高得8分。", "10")).isNotEmpty();
        assertThat(problems("分\n值", "10", "方案分档：优得3分，良得1分。", "12")).isNotEmpty();
    }
    private List<OriginalScoreEvidencePolicy.Problem> problems(String header, String score, String rule, String claimValue) {
        return problems(header, score, rule, claimValue, List.of("value"));
    }
    private List<OriginalScoreEvidencePolicy.Problem> problems(String header, String score, String rule, String claimValue, List<String> weightCells) {
        var cells = List.of(cell("h0", 0, 0, "序号"), cell("h1", 0, 1, "评价维度"),
                cell("h2", 0, 2, "评分细则"), cell("h3", 0, 3, header),
                cell("id", 1, 0, "1"), cell("name", 1, 1, "方案"), cell("rule", 1, 2, rule), cell("value", 1, 3, score));
        var table = new DocumentArtifact.CanonicalTable("t", 1, "评分办法", 2, 4, List.of(), cells, true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "synthetic", "PDF", "2.2"), List.of(table), List.of());
        var claim = new ScoreEvidenceClaim("fact", "fact", "t", "方案", List.of("id", "name", "rule", "value"),
                weightCells, new BigDecimal(claimValue), false, true, rule, "", "", "", ChildAggregation.SUM, null);
        var problems = new ArrayList<OriginalScoreEvidencePolicy.Problem>();
        OriginalScoreWeightEvidencePolicy.verifyWeight(artifact, claim, problems);
        return problems;
    }
    private DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int col, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, col, text, text, 1, 1, false, "synthetic:" + id);
    }
}
