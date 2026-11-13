package com.xiyu.bid.scoreparse.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ScoreResultReusePolicyTest {
    private final ScoreResultReusePolicy policy = new ScoreResultReusePolicy();
    private final ScoreResultReusePolicy.Identity current =
            new ScoreResultReusePolicy.Identity("file-a", "rules", "actual-score-v2","policy");
    @Test void failedTaskSuccessfulItemCanBePreservedButPendingCannot() {
        var source = new ScoreResultReusePolicy.Source(current, "COMPLETE", "SCORED", 7L);
        assertThat(policy.preserveEligible(current, source)).isTrue();
        assertThat(policy.preserveEligible(current,
                new ScoreResultReusePolicy.Source(current, "COMPLETE", "FAILED", 7L))).isFalse();
    }
    @Test void automaticSkipRequiresEveryTargetAndCompleteCoverage() {
        var scored = new ScoreResultReusePolicy.Source(current, "COMPLETE", "SCORED", 7L);
        var partial = new ScoreResultReusePolicy.Source(current, "PARTIAL", "SCORED", 7L);
        assertThat(policy.autoSkipEligible(current, java.util.List.of(scored), 1)).isTrue();
        assertThat(policy.autoSkipEligible(current, java.util.List.of(partial), 1)).isFalse();
        assertThat(policy.autoSkipEligible(current, java.util.List.of(scored), 2)).isFalse();
        assertThat(policy.preserveEligible(new ScoreResultReusePolicy.Identity("file-a","rules","actual-score-v2","changed-model"),scored)).isFalse();
        assertThat(policy.preserveEligible(new ScoreResultReusePolicy.Identity("file-b", "rules", "actual-score-v2","policy"), scored)).isFalse();
    }
}
