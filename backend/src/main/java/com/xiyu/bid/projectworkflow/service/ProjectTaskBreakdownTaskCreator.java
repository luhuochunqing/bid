package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ProjectTaskBreakdownTaskCreator {

    private final ProjectTaskWorkflowService projectTaskWorkflowService;
    private final TaskRepository taskRepository;

    Task createTask(Long projectId, TaskBreakdownPolicy.GeneratedTask generatedTask) {
        Task task = Task.builder()
                .projectId(projectId)
                .title(generatedTask.title())
                .description(generatedTask.description())
                .priority(toEntityPriority(generatedTask.priority()))
                .status(Task.Status.TODO)
                .dueDate(generatedTask.dueDate() == null ? null : generatedTask.dueDate().atTime(18, 0))
                .build();
        return taskRepository.save(task);
    }

    ProjectTaskViewDTO toTaskView(Task task) {
        return projectTaskWorkflowService.toTaskView(task);
    }

    private Task.Priority toEntityPriority(TaskBreakdownPolicy.TaskPriority priority) {
        if (priority == null) {
            return Task.Priority.MEDIUM;
        }
        return Task.Priority.valueOf(priority.name());
    }
}
