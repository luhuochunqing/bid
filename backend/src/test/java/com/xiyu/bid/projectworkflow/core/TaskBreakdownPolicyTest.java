package com.xiyu.bid.projectworkflow.core;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskBreakdownPolicyTest {

    @Test
    void decide_ShouldCreateBusinessTechnicalMaterialAndReviewTasks() {
        TaskBreakdownPolicy.Decision decision = TaskBreakdownPolicy.decide(new TaskBreakdownPolicy.Command(
                LocalDate.of(2026, 5, 20),
                List.of(
                        requirement("commercial", "商务条款响应", "按招标文件完成商务偏离表"),
                        requirement("technical", "技术实施方案", "提交平台对接和实施计划"),
                        requirement("material", "资格证明材料", "提供营业执照和资质证书"),
                        requirement("scoring", "评分点复核", "检查响应覆盖情况")
                ),
                List.of()
        ));

        assertThat(decision.tasks()).extracting(TaskBreakdownPolicy.GeneratedTask::title)
                .containsExactly(
                        "商务标：商务条款响应",
                        "技术标：技术实施方案",
                        "资料收集：资格证明材料",
                        "评分复核：评分点复核"
                );
        assertThat(decision.tasks()).extracting(TaskBreakdownPolicy.GeneratedTask::priority)
                .containsExactly(
                        TaskBreakdownPolicy.TaskPriority.HIGH,
                        TaskBreakdownPolicy.TaskPriority.HIGH,
                        TaskBreakdownPolicy.TaskPriority.MEDIUM,
                        TaskBreakdownPolicy.TaskPriority.MEDIUM
                );
        assertThat(decision.tasks()).extracting(TaskBreakdownPolicy.GeneratedTask::dueDate)
                .containsOnly(LocalDate.of(2026, 5, 15));
    }

    @Test
    void decide_ShouldSkipExistingTaskTitle() {
        TaskBreakdownPolicy.Decision decision = TaskBreakdownPolicy.decide(new TaskBreakdownPolicy.Command(
                LocalDate.of(2026, 5, 20),
                List.of(requirement("business", "商务条款响应", "按招标文件完成商务偏离表")),
                List.of(new TaskBreakdownPolicy.ExistingTaskSnapshot(" 商务标：商务条款响应 "))
        ));

        assertThat(decision.tasks()).isEmpty();
    }

    @Test
    void decide_ShouldReturnEmptyDecisionWhenNoSourcesAreUsable() {
        TaskBreakdownPolicy.Decision decision = TaskBreakdownPolicy.decide(new TaskBreakdownPolicy.Command(
                LocalDate.of(2026, 5, 20),
                List.of(requirement("technical", " ", " ")),
                List.of()
        ));

        assertThat(decision.tasks()).isEmpty();
    }

    private TaskBreakdownPolicy.SourceSnapshot requirement(String category, String title, String content) {
        return new TaskBreakdownPolicy.SourceSnapshot(category, title, content);
    }
}
