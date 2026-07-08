// Input: Task entity / task list, user repository, DTO mapper
// Output: TaskDTO with assignee and creator display names resolved
// Pos: Collaborator/任务名称解析
// 维护声明: 仅负责 Task → TaskDTO 转换中的名称解析（执行人/创建人）；不包含业务决策。
package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.dto.TaskDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 任务 DTO 名称解析协作器。
 *
 * <p>从 TaskService 拆分（ResponsibilityArchitectureTest 行预算治理），
 * 负责为 TaskDTO 填充 assigneeName 和 creatorName 展示名，避免 TaskService 职责过载。</p>
 *
 * <p>FP-Java Profile 合规：仅做名称解析编排，不含业务决策。</p>
 */
@Component
@RequiredArgsConstructor
public class TaskNameResolver {

    private final UserRepository userRepository;
    private final TaskDtoMapper taskDtoMapper;

    /**
     * 批量转换并注入执行人 + 创建人展示名，避免逐条查询（N+1）。
     */
    public List<TaskDTO> toDTOsWithNames(List<Task> tasks) {
        Map<Long, String> assigneeNames = userRepository
                .findAllById(tasks.stream().map(Task::getAssigneeId).filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getId, User::getFullName, (a, b) -> a));
        Map<String, String> creatorNames = userRepository
                .findAllByUsernameIn(tasks.stream().map(Task::getCreatedBy).filter(c -> c != null && !c.isBlank()).collect(Collectors.toSet()))
                .stream()
                .filter(u -> u.getFullName() != null && !u.getFullName().isBlank())
                .collect(Collectors.toMap(User::getUsername, User::getFullName, (a, b) -> a));
        return taskDtoMapper.toDTOs(tasks, assigneeNames, creatorNames);
    }

    /**
     * 单条转换并解析执行人 + 创建人展示名。
     */
    public TaskDTO toDTOWithNames(Task task) {
        return taskDtoMapper.toDTO(task, resolveAssigneeName(task.getAssigneeId()), resolveCreatorName(task.getCreatedBy()));
    }

    /**
     * 任务状态中文展示名（用于通知文案）。
     */
    public static String statusDisplayName(Task.Status s) {
        if (s == null) return "未知";
        return switch (s) {
            case TODO -> "待处理";
            case REVIEW -> "审核中";
            case COMPLETED -> "已完成";
        };
    }

    private String resolveAssigneeName(Long id) {
        return id == null ? null : userRepository.findById(id).map(User::getFullName).filter(n -> !n.isBlank()).orElse(null);
    }

    private String resolveCreatorName(String username) {
        return username == null || username.isBlank() ? null
                : userRepository.findByUsername(username).map(User::getFullName).filter(n -> n != null && !n.isBlank()).orElse(null);
    }
}
