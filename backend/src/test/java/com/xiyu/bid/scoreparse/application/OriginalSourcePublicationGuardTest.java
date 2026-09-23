package com.xiyu.bid.scoreparse.application;

import com.xiyu.bid.scoreparse.domain.OriginalScoringContext;
import com.xiyu.bid.scoreparse.domain.ScoreFact;
import com.xiyu.bid.scoreparse.domain.ScoreRule;
import com.xiyu.bid.scoreparse.domain.grid.DocumentArtifact;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OriginalSourcePublicationGuardTest {
    @Test
    void explicitSharedCapCannotPassDeterministicLegacySumDefault() {
        var artifact = new DocumentArtifact(new DocumentArtifact.ArtifactMeta("bid.pdf", "sha", "pdf", "2.0"),
                List.of(new DocumentArtifact.CanonicalTable("t", 1, "评分办法", 3, 2, List.of(), List.of(
                        cell("parent", 1, 0, "三个条件共享最高5分，满足任一条件即可"),
                        cell("a", 2, 0, "具备A条件得2分"),
                        cell("b", 3, 0, "具备B条件得3分")), true)), List.of());
        List<ScoreFact> facts = List.of(
                fact("p", "", "parent", "三个条件共享最高5分，满足任一条件即可", "5"),
                fact("a", "p", "a", "具备A条件得2分", "2"),
                fact("b", "p", "b", "具备B条件得3分", "3"));

        var result = OriginalSourcePublicationGuard.verify(
                OriginalScoringContext.resolve(artifact, "评分总分5分"), artifact, facts);

        assertThat(result.defects()).anyMatch(defect -> defect.startsWith("CHILD_AGGREGATION_UNSUPPORTED_BY_SOURCE"));
        assertThat(result.proofs()).isEmpty();
    }

    private static DocumentArtifact.CanonicalTable.CanonicalCellDto cell(String id, int row, int column, String raw) {
        return new DocumentArtifact.CanonicalTable.CanonicalCellDto(id, row, column, raw, raw,
                1, 1, false, "pdf:page=1;table=t");
    }

    private static ScoreFact fact(String id, String parentId, String cellId, String text, String weight) {
        return new ScoreFact(id, "t", List.of(cellId), text, id, text, new BigDecimal(weight), null,
                ScoreRule.MAX_SCORE, parentId, "TEST", 1.0, ScoreFact.ValidationStatus.AUTO_APPROVED);
    }
}
