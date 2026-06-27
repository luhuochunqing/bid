package com.xiyu.bid.task.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TaskTransitionPolicyTest {

    @Test
    void validateTransition_ShouldAllowTodoToReview() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.TODO,
                TaskTransitionPolicy.TaskStatus.REVIEW);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldRejectTodoToCompleted() {
        // CO-361 三态模型：TODO 不能直接跳到 COMPLETED，必须经 REVIEW
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.TODO,
                TaskTransitionPolicy.TaskStatus.COMPLETED);
        assertThat(result.allowed()).isFalse();
    }

    @Test
    void validateTransition_ShouldAllowReviewToCompleted() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.COMPLETED);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_ShouldAllowReviewBackToTodo() {
        var result = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.TODO);
        assertThat(result.allowed()).isTrue();
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
                TaskTransitionPolicy.TaskStatus.TODO,
                TaskTransitionPolicy.TaskStatus.TODO);
        assertThat(result.allowed()).isTrue();
    }

    @Test
    void validateTransition_NullStatuses_ShouldDeny() {
        var r1 = TaskTransitionPolicy.validateTransition(null, TaskTransitionPolicy.TaskStatus.REVIEW);
        var r2 = TaskTransitionPolicy.validateTransition(TaskTransitionPolicy.TaskStatus.TODO, null);
        assertThat(r1.allowed()).isFalse();
        assertThat(r2.allowed()).isFalse();
    }

    @Test
    void computeAutoStatusOnDeliverable_ShouldKeepTodo_WhenTodoAndFirstUpload() {
        var suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(
                TaskTransitionPolicy.TaskStatus.TODO, 0);
        assertThat(suggested).isEqualTo(TaskTransitionPolicy.TaskStatus.TODO);
    }

    @Test
    void computeAutoStatusOnDeliverable_ShouldKeepCurrentStatus() {
        var suggested = TaskTransitionPolicy.computeAutoStatusOnDeliverable(
                TaskTransitionPolicy.TaskStatus.REVIEW, 0);
        assertThat(suggested).isEqualTo(TaskTransitionPolicy.TaskStatus.REVIEW);
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
    void reviewToCompleted_DoesNotRequireComment() {
        var r = TaskTransitionPolicy.validateTransition(
                TaskTransitionPolicy.TaskStatus.REVIEW,
                TaskTransitionPolicy.TaskStatus.COMPLETED,
                null);
        assertThat(r.allowed()).isTrue();
    }
}
