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
    @Test void unrelatedNumericColumnAndExplicitContradictionDoNotProveWeight() {
        assertThat(problems("报价", "10", "方案分档：优得3分，良得1分。", "10")).isNotEmpty();
        assertThat(problems("分\n值", "10", "方案本项最高得8分。", "10")).isNotEmpty();
        assertThat(problems("分\n值", "10", "本评分标准仅评方案，本项最高得8分。", "10")).isNotEmpty();
        assertThat(problems("分\n值", "10", "方案分档：优得3分，良得1分。", "12")).isNotEmpty();
    }
    private List<OriginalScoreEvidencePolicy.Problem> problems(String header, String score, String rule, String claimValue) {
        var cells = List.of(cell("h0", 0, 0, "序号"), cell("h1", 0, 1, "评价维度"),
                cell("h2", 0, 2, "评分细则"), cell("h3", 0, 3, header),
                cell("id", 1, 0, "1"), cell("name", 1, 1, "方案"), cell("rule", 1, 2, rule), cell("value", 1, 3, score));
        var table = new DocumentArtifact.CanonicalTable("t", 1, "评分办法", 2, 4, List.of(), cells, true);
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("synthetic", "synthetic", "PDF", "2.2"), List.of(table), List.of());
        var claim = new ScoreEvidenceClaim("fact", "fact", "t", "方案", List.of("id", "name", "rule", "value"),
                List.of("value"), new BigDecimal(claimValue), false, true, rule, "", "", "", ChildAggregation.SUM);
        var problems = new ArrayList<OriginalScoreEvidencePolicy.Problem>();
        OriginalScoreWeightEvidencePolicy.verifyWeight(artifact, claim, problems);
        return problems;
    }
    private DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int col, String text) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, col, text, text, 1, 1, false, "synthetic:" + id);
    }
}
