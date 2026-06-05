package com.xiyu.bid.biddraftagent.domain;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ManualConfirmationBugTest {
    @Test
    void testRequiresConfirmationWithGaps() {
        ManualConfirmationPolicy policy = new ManualConfirmationPolicy();
        // create classification with no pricing, no legal, no qualification
        RequirementClassification classification = new RequirementClassification(
            List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of()
        );
        // create gaps that are NOT ready
        GapCheckResult gaps = new GapCheckResult(false, List.of("Some gap"), List.of("Suggestion"));

        ManualConfirmationDecision decision = policy.evaluate(classification, gaps);

        assertThat(decision.reasons()).contains("存在未闭合材料缺口，需人工确认后再定稿");

        // BUG: requiresConfirmation does not consider gaps!
        assertThat(decision.requiresConfirmation()).isTrue();
    }
}
