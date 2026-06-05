package com.xiyu.bid.projectworkflow.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.projectworkflow.core.TaskBreakdownPolicy;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
@RequiredArgsConstructor
public class ProjectTaskBreakdownService {

    private final ProjectWorkflowGuardService accessGuard;
    private final ProjectTaskBreakdownSourceReader sourceReader;
    private final ProjectTaskBreakdownTaskCreator taskCreator;
    private final TaskRepository taskRepository;
    private final ConcurrentMap<Long, Object> projectLocks = new ConcurrentHashMap<>();

    @Transactional
    public List<ProjectTaskViewDTO> decomposeProjectTasks(Long projectId) {
        Object lock = projectLocks.computeIfAbsent(projectId, ignored -> new Object());
        synchronized (lock) {
            return decomposeProjectTasksLocked(projectId);
        }
    }

    private List<ProjectTaskViewDTO> decomposeProjectTasksLocked(Long projectId) {
        Project project = accessGuard.requireWorkflowMutationProject(projectId);
        List<TaskBreakdownPolicy.SourceSnapshot> sources = sourceReader.collectRequirementSources(projectId);
        if (sources.isEmpty()) {
            sources = sourceReader.collectSectionSources(projectId);
        }
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("未找到可用于拆解任务的标书拆解结果");
        }

        List<Task> existingTasks = taskRepository.findByProjectId(projectId);
        TaskBreakdownPolicy.Decision decision = TaskBreakdownPolicy.decide(new TaskBreakdownPolicy.Command(
                project.getDeadline(),
                sources,
                existingTasks.stream()
                        .map(task -> new TaskBreakdownPolicy.ExistingTaskSnapshot(task.getTitle()))
                        .toList()
        ));

        decision.tasks().forEach(task -> taskCreator.createTask(projectId, task));
        return taskRepository.findByProjectId(projectId).stream()
                .map(taskCreator::toTaskView)
                .toList();
    }
}
