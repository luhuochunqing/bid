package com.xiyu.bid.task.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;

class BidSubmissionPolicyTest {

    @Test
    void validateSubmission_ShouldAccept_AllTasksCompleteWithDeliverables() {
        var result = BidSubmissionPolicy.validateSubmission(5, 5, 3);
        assertThat(result.submittable()).isTrue();
        assertThat(result.reason()).isEmpty();
        assertThat(result.gaps()).isEmpty();
    }

    @Test
    void validateSubmission_ShouldReject_IncompleteTasks() {
        var result = BidSubmissionPolicy.validateSubmission(5, 3, 2);
        assertThat(result.submittable()).isFalse();
        assertThat(result.gaps())
                .anyMatch(g -> g.description().contains("未完成"));
    }

    @Test
    void validateSubmission_ShouldReject_NoDeliverablesAtAll() {
        var result = BidSubmissionPolicy.validateSubmission(4, 4, 0);
        assertThat(result.submittable()).isFalse();
        assertThat(result.gaps())
                .anyMatch(g -> g.description().contains("交付物"));
    }

    @Test
    void validateSubmission_ShouldReject_MultipleGaps() {
        var result = BidSubmissionPolicy.validateSubmission(6, 4, 0);
        assertThat(result.submittable()).isFalse();
        assertThat(result.gaps()).hasSize(2);
    }

    @Test
    void validateSubmission_ZeroTasks_ShouldBeSubmittable() {
        var result = BidSubmissionPolicy.validateSubmission(0, 0, 0);
        assertThat(result.submittable()).isTrue();
    }

    @Test
    void validateSubmission_AllTasksCompleteWithDeliverables_ShouldAccept() {
        var result = BidSubmissionPolicy.validateSubmission(3, 3, 3);
        assertThat(result.submittable()).isTrue();

        var result2 = BidSubmissionPolicy.validateSubmission(3, 3, 3);
        assertThat(result2.submittable()).isTrue();
    }

    @Test
    void submissionValidationResult_ShouldDefensivelyCopyGaps() {
        ArrayList<BidSubmissionPolicy.TaskGap> gaps = new ArrayList<>(List.of(
                new BidSubmissionPolicy.TaskGap(1L, "任务A", "缺少交付物")
        ));

        BidSubmissionPolicy.SubmissionValidationResult result =
                new BidSubmissionPolicy.SubmissionValidationResult(false, "fail", gaps);

        gaps.add(new BidSubmissionPolicy.TaskGap(2L, "任务B", "未完成"));

        assertThat(result.gaps()).hasSize(1);
        assertThatThrownBy(() -> result.gaps().add(new BidSubmissionPolicy.TaskGap(3L, "任务C", "其他")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
