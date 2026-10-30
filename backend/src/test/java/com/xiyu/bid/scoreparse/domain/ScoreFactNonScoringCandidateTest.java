package com.xiyu.bid.scoreparse.domain;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreFactNonScoringCandidateTest {
    @Test void explicitNonScoringZeroPointRowsRemainFactsButNotScoringCandidates() {
        assertThat(fact("否决项（不计\n分）", "0").toCandidate()).isEmpty();
        assertThat(fact("评审说明（不计分）", "0").toCandidate()).isEmpty();
    }
    @Test void zeroAloneAndPositiveScoreWithWordsAreNotSilentlyDiscarded() {
        assertThat(fact("信用扣分", "0").toCandidate()).isPresent();
        assertThat(fact("说明不计分，但本项另有评分", "5").toCandidate()).isPresent();
    }
    private ScoreFact fact(String dimension, String weight) {
        return new ScoreFact("f", "t", List.of("cell"), "source", "1", dimension,
                new BigDecimal(weight), null, ScoreRule.MAX_SCORE, "", "synthetic", 1.0,
                ScoreFact.ValidationStatus.AUTO_APPROVED, ChildAggregation.SUM, null);
    }
}
