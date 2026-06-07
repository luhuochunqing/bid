package com.xiyu.bid.task.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTransitionPolicyTest {

    @Test
    void validateTransition_ShouldAllowTodoToInProgress() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.TODO,
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldAllowTodoToCancelled() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.TODO,
                TaskTransitionPolicy.TaskStatus.CANCELLED);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldAllowInProgressToReview() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS,
                TaskTransitionPolicy.TaskStatus.REVIEW);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldAllowReviewToCompleted() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.COMPLETED);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldAllowReviewBackToInProgress() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldAllowCancelledReactivation() {
        var r1 = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.CANCELLED,
                TaskTransitionPolicy.TaskStatus.TODO);
        var r2 = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.CANCELLED,
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
        assertThat(r1.allowed()).isTrue();
        assertThat(r2.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldRejectCompletedToTodo() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.COMPLETED,
                TaskTransitionPolicy.TaskStatus.TODO);
        assertThat(result.allowed()).isFalse();
        assertThat(result.reason()).contains("不允许");
    }

    @Test
    void validateTransition_ShouldRejectCompletedToAny() {
        for (TaskTransitionPolicy.TaskStatus target : TaskTransitionPolicy.TaskStatus.values()) {
            if (target == TaskTransitionPolicy.TaskStatus.COMPLETED) continue;
            var result = TaskTransitionPolicy.validateTransition(
                    TaskTransitionPolicy.TaskStatus.COMPLETED, target);
            assertThat(result.allowed())
                    .as("COMPLETED -> %s should be denied", target)
                    .isFalse();
        }
    }

    @Test
    void validateTransition_SameStatus_ShouldBeOk() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS,
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_NullStatuses_ShouldDeny() {
        var r1 = TaskTransitionPolicy.validateTransition(null, TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
        var r2 = TaskTransitionPolicy.validateTransition(TaskTransitionPolicy.TaskStatus.TODO, null);
        assertThat(r1.allowed()).isFalse();
        assertThat(r2.allowed()).isFalse();
    }

    @Test
    void computeAutoStatusOnDeliverable_ShouldSuggestProgress_WhenTodoAndFirstUpload() {
        var suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(
                TaskTransitionPolicy.TaskStatus.TODO, 0);
        assertThat(suggested).isEqualTo(TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
    }

    @Test
    void computeAutoStatusOnDeliverable_ShouldNotSuggest_WhenAlreadyInProgress() {
        var suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(
                TaskTransitionPolicy.TaskStatus.IN_PROGRESS, 0);
        assertThat(suggested).isEqualTo(TaskTransitionPolicy.TaskStatus.IN_PROGRESS);
    }

    @Test
    void computeAutoStatusOnDeliverable_ShouldReturnCurrent_WhenHasExisting() {
        var suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(
                TaskTransitionPolicy.TaskStatus.TODO, 3);
        assertThat(suggested).isEqualTo(TaskTransitionPolicy.TaskStatus.TODO);
    }

    @Test
    void computeAutoStatusOnDeliverable_NullCurrent_ShouldDefaultToTodo() {
        var suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(null, 0);
        assertThat(suggested).isEqualTo(TaskTransitionPolicy.TaskStatus.TODO);
    }

    // ---- PRD §3.2.2 退回待办（REVIEW -> TODO）必须带 reviewComment ----

    @Test
    void reviewToTodo_WithoutComment_Denied() {
        var r = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.TODO,
                null);
        assertThat(r.allowed()).isFalse();
        assertThat(r.reason()).contains("reviewComment");
    }

    @Test
    void reviewToTodo_BlankComment_Denied() {
        var r = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.TODO,
                "   ");
        assertThat(r.allowed()).isFalse();
    }

    @Test
    void reviewToTodo_WithReason_Allowed() {
        var r = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.TODO,
                "需要补充财务数据");
        assertThat(r.allowed()).isTrue();
    }

    @Test
    void reviewToTodo_NewPathAllowedInBaseValidator() {
        // legacy 2-arg signature now allows REVIEW -> TODO (reviewer enforces reason)
        var r = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.TODO);
        assertThat(r.allowed()).isTrue();
    }

    @Test
    void reviewToCompleted_DoesNotRequireComment() {
        var r = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.COMPLETED,
                null);
        assertThat(r.allowed()).isTrue();
    }
}
