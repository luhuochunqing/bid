package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.Task;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TaskProjectVisibilityPolicyTest {

    @Test
    void nullProjectId_returnsTrue() {
        assertThat(TaskProjectVisibilityPolicy.canAccessProject(null, List.of(1L, 2L))).isTrue();
    }

    @Test
    void nullAllowedIds_returnsTrue() {
        assertThat(TaskProjectVisibilityPolicy.canAccessProject(10L, null)).isTrue();
    }

    @Test
    void emptyAllowedProjectIdsMeansUnrestrictedAccess() {
        assertThat(TaskProjectVisibilityPolicy.canAccessProject(10L, List.of())).isTrue();
    }

    @Test
    void matchingProjectId_returnsTrue() {
        assertThat(TaskProjectVisibilityPolicy.canAccessProject(10L, List.of(10L, 20L))).isTrue();
    }

    @Test
    void nonMatchingProjectId_returnsFalse() {
        assertThat(TaskProjectVisibilityPolicy.canAccessProject(99L, List.of(10L, 20L))).isFalse();
    }

    @Test
    void filtersTasksByAllowedProjectIds() {
        List<Task> tasks = List.of(task(1L, 10L), task(2L, 20L));

        List<Task> visibleTasks = TaskProjectVisibilityPolicy.filterVisibleTasks(tasks, List.of(10L));

        assertThat(visibleTasks).extracting(Task::getId).containsExactly(1L);
    }

    @Test
    void filterTasks_nullProjectId_keepsTask() {
        List<Task> tasks = List.of(task(1L, null));

        List<Task> visibleTasks = TaskProjectVisibilityPolicy.filterVisibleTasks(tasks, List.of(10L));

        assertThat(visibleTasks).extracting(Task::getId).containsExactly(1L);
    }

    @Test
    void filterTasks_emptyAllowedIds_keepsAll() {
        List<Task> tasks = List.of(task(1L, 10L), task(2L, null));

        List<Task> visible = TaskProjectVisibilityPolicy.filterVisibleTasks(tasks, List.of());

        assertThat(visible).hasSize(2);
    }

    private Task task(Long id, Long projectId) {
        return Task.builder()
                .id(id)
                .projectId(projectId)
                .title("任务")
                .status(Task.Status.TODO)
                .priority(Task.Priority.MEDIUM)
                .build();
    }
}
