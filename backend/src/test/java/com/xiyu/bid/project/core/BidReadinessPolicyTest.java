package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for {@link BidReadinessPolicy}.
 * No Spring context, no mocks — verifies ready/not-ready decisions.
 */
class BidReadinessPolicyTest {

    @Test
    void permits_whenAllTasksCompleted_andHasBidDocument() {
        var decision = BidReadinessPolicy.check(
                List.of(AllTasksCompletedPolicy.TaskState.COMPLETED,
                        AllTasksCompletedPolicy.TaskState.COMPLETED),
                true);
        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void denies_whenTasksIncomplete_evenIfHasDocument() {
        var decision = BidReadinessPolicy.check(
                List.of(AllTasksCompletedPolicy.TaskState.COMPLETED,
                        AllTasksCompletedPolicy.TaskState.TODO),
                true);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("1 个任务未完成");
    }

    @Test
    void denies_whenNoBidDocument_evenIfAllTasksCompleted() {
        var decision = BidReadinessPolicy.check(
                List.of(AllTasksCompletedPolicy.TaskState.COMPLETED),
                false);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("尚未上传标书文件");
    }

    @Test
    void denies_whenBothFailures_prefersTaskGatingMessage() {
        // 任务问题优先级高，先报告
        var decision = BidReadinessPolicy.check(
                List.of(AllTasksCompletedPolicy.TaskState.REVIEW),
                false);
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("任务未完成");
    }

    @Test
    void permits_whenNoTasks_atAll() {
        var decision = BidReadinessPolicy.check(List.of(), true);
        assertThat(decision.allowed()).isTrue();
    }
}