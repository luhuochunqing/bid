package com.xiyu.bid.scoreparse.domain;

import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WeightScopeConsistencyTest {
    @Test void numberedTextAloneCannotProveChildRelationship() {
        // A numbered clause may be a material requirement; without a verified parent/child graph,
        // the lower maximum remains uncertain instead of being silently declared compatible.
        var errors = verify("1.商品品种满分10分。2.方案（2分）和配合（3分）。");
        assertThat(errors).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .contains("ITEM_WEIGHT_SOURCE_UNKNOWN").doesNotContain("ITEM_WEIGHT_SOURCE_MISMATCH");
    }
    @Test void establishedParentCanUseItsOwnFifteenPointMaximum() {
        var errors = verify("1.商品品种满分10分。2.方案（2分）和配合（3分）。", true);
        assertThat(errors).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .doesNotContain("ITEM_WEIGHT_SOURCE_UNKNOWN", "ITEM_WEIGHT_SOURCE_MISMATCH");
    }
    @Test void explicitlyContradictoryWholeRowMaximumRemainsBlocked() {
        var errors = verify("商品品种满分10分，本项满分20分。");
        assertThat(errors).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .containsAnyOf("ITEM_WEIGHT_SOURCE_UNKNOWN", "ITEM_WEIGHT_SOURCE_MISMATCH");
    }
    @Test void establishedParentDoesNotHideAnExplicitConflictingMaximum() {
        var errors = verify("商品品种满分10分，本项满分20分。", true);
        assertThat(errors).extracting(OriginalScoreEvidencePolicy.Problem::code)
                .containsAnyOf("ITEM_WEIGHT_SOURCE_UNKNOWN", "ITEM_WEIGHT_SOURCE_MISMATCH");
    }
    private static List<OriginalScoreEvidencePolicy.Problem> verify(String rule) {
        return verify(rule, false);
    }
    private static List<OriginalScoreEvidencePolicy.Problem> verify(String rule, boolean parent) {
        var cells = new ArrayList<DocumentArtifact.CanonicalTable.CanonicalCellDto>();
        String[][] rows = {{"条款号", "评审因素", "评审标准", "分值"},
                {"2", "商品保障能力（15分）", rule, "15"}};
        for (int r=0; r<rows.length; r++) for (int c=0; c<4; c++)
            cells.add(new DocumentArtifact.CanonicalTable.CanonicalCellDto("t-r"+r+"-c"+c,r,c,rows[r][c],rows[r][c],1,1,false,"pdf:page=14",14,"t-r"+r));
        var table = new DocumentArtifact.CanonicalTable("t",13,"第四章 评审办法",2,4,List.of(),cells,true,"PDF");
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("doc","hash","PDF","2.2"),List.of(table),List.of());
        var claim = new ScoreEvidenceClaim("f","f","t","商品保障能力",List.of("t-r1-c1", "t-r1-c2", "t-r1-c3"),
                List.of("t-r1-c3"),new BigDecimal("15"),parent,true,"商品保障能力",null,"","",ChildAggregation.SUM, null);
        var errors = new ArrayList<OriginalScoreEvidencePolicy.Problem>();
        OriginalScoreWeightEvidencePolicy.verifyWeight(artifact,claim,errors);
        return errors;
    }
}
