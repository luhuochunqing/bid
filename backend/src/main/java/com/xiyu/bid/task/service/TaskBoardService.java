package com.xiyu.bid.task.service;

import com.xiyu.bid.entity.Project;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.project.entity.BidDocumentReviewEntity;
import com.xiyu.bid.project.repository.BidDocumentReviewRepository;
import com.xiyu.bid.repository.ProjectRepository;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.dto.TaskBoardItemDTO;
import com.xiyu.bid.task.entity.TaskDeliverable;
import com.xiyu.bid.task.repository.TaskDeliverableRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * 独立任务看板应用服务。
 *
 * <p>编排 task 表与项目工作流待办（如标书审核）的查询与组装，
 * 纯映射逻辑下沉到 {@link TaskBoardItemMapper}。</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskBoardService {

    private final TaskRepository taskRepository;
    private final BidDocumentReviewRepository bidDocumentReviewRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final TaskAssignmentSupport assignmentSupport;
    private final TaskDeliverableRepository taskDeliverableRepository;

    /**
     * 获取当前登录用户的任务看板条目。
     *
     * <p>返回普通任务（当前用户为 assignee）与待审标书（当前用户为 reviewer）。
     * 结果按项目可见性过滤，确保用户只能看到其有权限访问的项目数据。</p>
     *
     * @param username 当前登录用户名
     * @return 看板条目列表（按类型无关排序）
     */
    public List<TaskBoardItemDTO> getBoardItems(String username) {
        User currentUser = assignmentSupport.resolveEnabledUserByUsername(username);
        Long userId = currentUser.getId();

        List<Task> tasks = taskRepository.findByAssigneeId(userId).stream()
                .filter(TaskBoardItemMapper::isVisibleTask)
                .toList();

        List<BidDocumentReviewEntity> reviews = bidDocumentReviewRepository.findByReviewerId(userId).stream()
                .filter(TaskBoardItemMapper::isActiveBidReview)
                .toList();

        Set<Long> projectIds = Stream.concat(
                tasks.stream().map(Task::getProjectId),
                reviews.stream().map(BidDocumentReviewEntity::getProjectId)
        ).filter(Objects::nonNull).collect(Collectors.toSet());

        Map<Long, String> projectNames = fetchProjectNames(projectIds);
        Map<Long, String> submitterNames = fetchSubmitterNames(reviews);

        // Batch-load deliverables for all tasks
        Map<Long, List<TaskDeliverable>> deliverablesByTaskId = batchLoadDeliverables(tasks);

        List<TaskBoardItemDTO> items = new ArrayList<>(tasks.size() + reviews.size());
        String assigneeName = TaskBoardItemMapper.fullNameOf(currentUser);
        for (Task task : tasks) {
            List<TaskDeliverable> deliverables = deliverablesByTaskId.getOrDefault(task.getId(), List.of());
            items.add(TaskBoardItemMapper.fromTask(task, projectNames, assigneeName, deliverables));
        }
        for (BidDocumentReviewEntity review : reviews) {
            items.add(TaskBoardItemMapper.fromBidReview(review, projectNames, submitterNames));
        }

        return filterByProjectVisibility(items, currentUser);
    }

    private Map<Long, String> fetchProjectNames(Set<Long> projectIds) {
        if (projectIds.isEmpty()) {
            return Map.of();
        }
        return projectRepository.findAllById(projectIds).stream()
                .collect(Collectors.toMap(Project::getId, p -> {
                    String name = p.getName();
                    return name == null ? "" : name;
                }));
    }

    private Map<Long, String> fetchSubmitterNames(List<BidDocumentReviewEntity> reviews) {
        Set<Long> submitterIds = reviews.stream()
                .map(BidDocumentReviewEntity::getSubmittedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (submitterIds.isEmpty()) {
            return Map.of();
        }
        return userRepository.findByIdIn(submitterIds).stream()
                .collect(Collectors.toMap(User::getId, TaskBoardItemMapper::fullNameOf));
    }

    private List<TaskBoardItemDTO> filterByProjectVisibility(List<TaskBoardItemDTO> items, User currentUser) {
        List<Long> allowedProjectIds = projectAccessScopeService.getAllowedProjectIds(currentUser);
        if (allowedProjectIds.isEmpty()) {
            return items;
        }
        Set<Long> allowed = Set.copyOf(allowedProjectIds);
        return items.stream()
                .filter(item -> item.getProjectId() != null && allowed.contains(item.getProjectId()))
                .toList();
    }

    private Map<Long, List<TaskDeliverable>> batchLoadDeliverables(List<Task> tasks) {
        List<Long> taskIds = tasks.stream().map(Task::getId).filter(Objects::nonNull).toList();
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return taskDeliverableRepository.findByTaskIdIn(taskIds).stream()
                .collect(Collectors.groupingBy(TaskDeliverable::getTaskId));
    }
}
