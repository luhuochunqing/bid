package com.xiyu.bid.task.core;

import com.xiyu.bid.entity.Task;

import java.util.Collection;
import java.util.List;

public final class TaskProjectVisibilityPolicy {

    private TaskProjectVisibilityPolicy() {
    }

    public static boolean canAccessProject(Long projectId, Collection<Long> allowedProjectIds) {
        if (projectId == null) {
            return true;
        }
        if (allowedProjectIds == null || allowedProjectIds.isEmpty()) {
            return true;
        }
        return allowedProjectIds.contains(projectId);
    }

    public static List<Task> filterVisibleTasks(List<Task> tasks, Collection<Long> allowedProjectIds) {
        if (allowedProjectIds == null || allowedProjectIds.isEmpty()) {
            return List.copyOf(tasks);
        }
        return tasks.stream()
                .filter(task -> canAccessProject(task.getProjectId(), allowedProjectIds))
                .toList();
    }
}
