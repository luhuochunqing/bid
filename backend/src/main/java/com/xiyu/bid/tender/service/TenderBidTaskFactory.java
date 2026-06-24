// Input: TaskRepository, TaskService
// Output: 复用或创建标讯立项待办任务
// Pos: Service/业务编排层
package com.xiyu.bid.tender.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.task.dto.TaskDTO;
import com.xiyu.bid.task.service.TaskService;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 复用 participate 创建的立项待办任务；不存在时再新建，避免重复。
 */
@Component
public class TenderBidTaskFactory {

    private final TaskRepository taskRepository;
    private final TaskService taskService;

    public TenderBidTaskFactory(TaskRepository taskRepository, TaskService taskService) {
        this.taskRepository = taskRepository;
        this.taskService = taskService;
    }

    public TaskDTO reuseOrCreate(Long tenderId, Long projectId, String tenderTitle, Long assigneeId) {
        String initiationTitle = "【待立项】" + tenderTitle;
        Optional<Task> existing = taskRepository.findByProjectId(tenderId).stream()
                .filter(t -> t.getStatus() == Task.Status.TODO && initiationTitle.equals(t.getTitle()))
                .findFirst();

        if (existing.isPresent()) {
            Task task = existing.get();
            task.setProjectId(projectId);
            task.setAssigneeId(assigneeId);
            return taskService.getTaskById(taskRepository.save(task).getId());
        }

        return taskService.createTask(TaskDTO.builder()
                .projectId(projectId)
                .title(initiationTitle)
                .description("标讯「" + tenderTitle + "」已投标，请项目经理尽快完成立项流程。")
                .assigneeId(assigneeId)
                .status(Task.Status.TODO)
                .priority(Task.Priority.HIGH)
                .build());
    }
}
