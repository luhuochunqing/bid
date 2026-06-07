package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScoreDraftPolicyTest {

    @Test
    void decideUpdate_shouldAutoPromoteToReadyWhenAssigneePresent() {
        ScoreDraftPolicy.UpdateDecision decision = ScoreDraftPolicy.decideUpdate(
            new ScoreDraftPolicy.UpdateCommand(
                ScoreDraftPolicy.DraftStatus.DRAFT,
                3001L,
                " 王工 ",
                LocalDateTime.of(2026, 5, 1, 18, 0),
                "  整理商务资质  ",
                "  准备资质材料  ",
                null,
                null
            )
        );

        assertThat(decision.ok()).isTrue();
        assertThat(decision.status()).isEqualTo(ScoreDraftPolicy.DraftStatus.READY);
        assertThat(decision.assigneeId()).isEqualTo(3001L);
        assertThat(decision.assigneeName()).isEqualTo("王工");
        assertThat(decision.generatedTaskTitle()).isEqualTo("整理商务资质");
        assertThat(decision.generatedTaskDescription()).isEqualTo("准备资质材料");
    }

    @Test
    void decideUpdate_shouldRejectReadyWithoutAssignee() {
        ScoreDraftPolicy.UpdateDecision decision = ScoreDraftPolicy.decideUpdate(
            new ScoreDraftPolicy.UpdateCommand(
                ScoreDraftPolicy.DraftStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                ScoreDraftPolicy.DraftStatus.READY,
                null
            )
        );

        assertThat(decision.ok()).isFalse();
        assertThat(decision.failure()).isEqualTo(ScoreDraftPolicy.RuleFailure.READY_REQUIRES_ASSIGNEE);
    }

    @Test
    void decideUpdate_shouldSupplyDefaultSkipReason() {
        ScoreDraftPolicy.UpdateDecision decision = ScoreDraftPolicy.decideUpdate(
            new ScoreDraftPolicy.UpdateCommand(
                ScoreDraftPolicy.DraftStatus.DRAFT,
                null,
                null,
                null,
                null,
                null,
                ScoreDraftPolicy.DraftStatus.SKIPPED,
                "   "
            )
        );

        assertThat(decision.ok()).isTrue();
        assertThat(decision.status()).isEqualTo(ScoreDraftPolicy.DraftStatus.SKIPPED);
        assertThat(decision.skipReason()).isEqualTo("人工暂不生成");
    }

    @Test
    void decideUpdate_shouldRejectChangesToGeneratedDraft() {
        ScoreDraftPolicy.UpdateDecision decision = ScoreDraftPolicy.decideUpdate(
            new ScoreDraftPolicy.UpdateCommand(
                ScoreDraftPolicy.DraftStatus.GENERATED,
                3001L,
                null,
                null,
                null,
                null,
                null,
                null
            )
        );

        assertThat(decision.ok()).isFalse();
        assertThat(decision.failure()).isEqualTo(ScoreDraftPolicy.RuleFailure.GENERATED_NOT_EDITABLE);
    }

    @Test
    void decideGeneration_shouldRequireAllDraftsReady() {
        ScoreDraftPolicy.GenerationDecision decision = ScoreDraftPolicy.decideGeneration(
            List.of(
                ScoreDraftPolicy.DraftStatus.READY,
                ScoreDraftPolicy.DraftStatus.DRAFT
            )
        );

        assertThat(decision.ok()).isFalse();
        assertThat(decision.failure()).isEqualTo(ScoreDraftPolicy.RuleFailure.ONLY_READY_DRAFTS_CAN_GENERATE_TASKS);
    }
}
