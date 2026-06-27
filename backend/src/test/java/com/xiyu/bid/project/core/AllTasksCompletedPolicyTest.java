// Input: AllTasksCompletedPolicy 行为
// Output: 覆盖空、全完成、单未完成、混合
// Pos: backend test source
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
package com.xiyu.bid.project.core;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AllTasksCompletedPolicyTest {

    @Test
    void empty_allow_with_zero_incomplete() {
        var d = AllTasksCompletedPolicy.decide(List.of());
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void all_completed_allow() {
        var d = AllTasksCompletedPolicy.decide(List.of(
                AllTasksCompletedPolicy.TaskState.COMPLETED,
                AllTasksCompletedPolicy.TaskState.COMPLETED));
        assertThat(d.allowed()).isTrue();
    }

    @Test
    void single_incomplete_denies_with_count_1() {
        var d = AllTasksCompletedPolicy.decide(List.of(
                AllTasksCompletedPolicy.TaskState.COMPLETED,
                AllTasksCompletedPolicy.TaskState.TODO));
        assertThat(d.allowed()).isFalse();
        assertThat(((AllTasksCompletedPolicy.Decision.Deny) d).incompleteCount()).isEqualTo(1);
    }

    @Test
    void mixed_incomplete_counted() {
        var d = AllTasksCompletedPolicy.decide(List.of(
                AllTasksCompletedPolicy.TaskState.TODO,
                AllTasksCompletedPolicy.TaskState.REVIEW,
                AllTasksCompletedPolicy.TaskState.COMPLETED));
        assertThat(d.allowed()).isFalse();
        assertThat(((AllTasksCompletedPolicy.Decision.Deny) d).incompleteCount()).isEqualTo(2);
    }

    @Test
    void null_list_denies_safely() {
        var d = AllTasksCompletedPolicy.decide(null);
        assertThat(d.allowed()).isFalse();
    }
}
