// Input: mutable Task entity
// Output: detached Task copy for before/after history comparisons
// Pos: Helper/任务快照复制
package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;

public final class TaskSnapshots {

    private TaskSnapshots() {
    }

    public static Task copy(Task task) {
        return Task.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .content(task.getContent())
                .extendedFieldsJson(task.getExtendedFieldsJson())
                .assigneeId(task.getAssigneeId())
                .assigneeDeptCode(task.getAssigneeDeptCode())
                .assigneeDeptName(task.getAssigneeDeptName())
                .assigneeRoleCode(task.getAssigneeRoleCode())
                .assigneeRoleName(task.getAssigneeRoleName())
                .status(task.getStatus())
                .priority(task.getPriority())
                .dueDate(task.getDueDate())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }
}
