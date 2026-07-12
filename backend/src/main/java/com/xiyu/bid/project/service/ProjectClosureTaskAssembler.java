// Input: Task 数据 + AllTasksCompletedPolicy 适配
// Output: 结项服务所需的任务状态快照和 GatePolicy 适配（数据访问 + 映射，不含业务决策）
// Pos: project/service/ - 应用服务辅助层
package com.xiyu.bid.project.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.project.core.AllTasksCompletedPolicy;
import com.xiyu.bid.project.core.ProjectClosureGatePolicy;
import com.xiyu.bid.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
@RequiredArgsConstructor
public final class ProjectClosureTaskAssembler {

    private final TaskRepository taskRepository;

    public TaskSummary loadTaskSummary(Long projectId) {
        List<Task> tasks = taskRepository.findByProjectId(projectId);
        if (tasks == null || tasks.isEmpty()) {
            return new TaskSummary(0, 0, 0, List.of());
        }
        int total = tasks.size();
        int completed = (int) tasks.stream()
                .filter(t -> t.getStatus() == Task.Status.COMPLETED)
                .count();
        int incomplete = total - completed;
        List<AllTasksCompletedPolicy.TaskState> states = tasks.stream()
                .map(this::toPolicyState)
                .toList();
        return new TaskSummary(total, completed, incomplete, states);
    }

    public ProjectClosureGatePolicy.ClosureGateInputs buildGateInputs(
            ProjectClosureGatePolicy.DepositSnapshot depositSnapshot,
            ProjectClosureGatePolicy.ClosureInput closureInput,
            Long projectId,
            BigDecimal depositAmount) {
        TaskSummary summary = loadTaskSummary(projectId);
        return new ProjectClosureGatePolicy.ClosureGateInputs(
                depositSnapshot, closureInput, summary.taskStates(), depositAmount);
    }

    private AllTasksCompletedPolicy.TaskState toPolicyState(Task task) {
        if (task == null || task.getStatus() == null) {
            return AllTasksCompletedPolicy.TaskState.TODO;
        }
        return switch (task.getStatus()) {
            case TODO -> AllTasksCompletedPolicy.TaskState.TODO;
            case REVIEW -> AllTasksCompletedPolicy.TaskState.REVIEW;
            case COMPLETED -> AllTasksCompletedPolicy.TaskState.COMPLETED;
        };
    }

    public record TaskSummary(
            int totalTaskCount,
            int completedTaskCount,
            int incompleteTaskCount,
            List<AllTasksCompletedPolicy.TaskState> taskStates) {
    }
}
