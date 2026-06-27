package com.xiyu.bid.projectworkflow.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.notification.core.NotificationType;
import com.xiyu.bid.notification.dto.CreateNotificationRequest;
import com.xiyu.bid.notification.service.NotificationApplicationService;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskCreateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskStatusUpdateRequest;
import com.xiyu.bid.projectworkflow.dto.ProjectTaskViewDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectScoreDraft;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.service.TaskHistoryRecorder;
import com.xiyu.bid.task.service.TaskSnapshots;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
class ProjectTaskWorkflowService {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.CHINA);
    private static final TypeReference<Map<String, Object>> EXTENDED_FIELDS_TYPE =
            new TypeReference<>() {};

    private final ProjectWorkflowGuardService guardService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final TaskHistoryRecorder taskHistoryRecorder;
    private final ProjectTaskDeliverableCollector deliverableCollector;
    private final NotificationApplicationService notificationService;

    List<ProjectTaskViewDTO> getProjectTasks(Long projectId) {
        guardService.requireProject(projectId);
        return taskRepository.findByProjectId(projectId).stream()
                .map(this::toTaskView)
                .toList();
    }

    ProjectTaskViewDTO createProjectTask(Long projectId, ProjectTaskCreateRequest request) {
        return createProjectTask(projectId, request, null);
    }

    ProjectTaskViewDTO createProjectTask(Long projectId, ProjectTaskCreateRequest request, String creatorUsername) {
        guardService.requireWorkflowMutationProject(projectId);
        User assigneeUser = resolveAssignee(request.getAssigneeId(), creatorUsername);
        Task task = Task.builder()
                .projectId(projectId)
                .title(request.getTitle().trim())
                .description(trimToNull(request.getDescription()))
                .content(request.getContent())
                .extendedFieldsJson(serializeExtendedFields(request.getExtendedFields()))
                .assigneeId(assigneeUser != null ? assigneeUser.getId() : request.getAssigneeId())
                .assigneeDeptCode(assigneeUser != null ? assigneeUser.getDepartmentCode() : trimToNull(request.getAssigneeDeptCode()))
                .assigneeDeptName(assigneeUser != null ? assigneeUser.getDepartmentName() : defaultString(trimToNull(request.getAssigneeDeptName()), "未配置部门"))
                .assigneeRoleCode(assigneeUser != null ? assigneeUser.getRoleCode() : trimToNull(request.getAssigneeRoleCode()))
                .assigneeRoleName(assigneeUser != null ? assigneeUser.getRoleName() : trimToNull(request.getAssigneeRoleName()))
                .priority(toEntityPriority(request.getPriority()))
                .status(Task.Status.TODO)
                .dueDate(request.getDueDate())
                .build();
        Task saved = taskRepository.save(task);
        String assigneeName = assigneeUser != null ? assigneeUser.getFullName() : request.getAssigneeName();
        if (saved.getAssigneeId() != null) {
            try {
                notificationService.createNotification(
                        new CreateNotificationRequest(NotificationType.INFO.name(), "TASK", saved.getId(),
                                "新任务分配：" + saved.getTitle(),
                                saved.getDescription() != null ? saved.getDescription() : "请在投标项目中查看任务详情",
                                Map.of("projectId", String.valueOf(saved.getProjectId())),
                                Collections.singletonList(saved.getAssigneeId())),
                        saved.getAssigneeId());
            } catch (RuntimeException e) {
                log.warn("Failed to notify assignee for task {}: {}", saved.getId(), e.getMessage());
            }
        }
        return toTaskView(saved, assigneeName);
    }

    ProjectTaskViewDTO updateProjectTaskStatus(
            Long projectId,
            Long taskId,
            ProjectTaskStatusUpdateRequest request,
            String actorUsername
    ) {
        guardService.requireWorkflowMutationProject(projectId);
        Task task = guardService.requireTask(projectId, taskId);
        Task.Status targetStatus = toEntityStatus(request.getStatus());

        // REVIEW -> TODO (驳回) 时校验 reviewComment 非空
        if (task.getStatus() == Task.Status.REVIEW && targetStatus == Task.Status.TODO) {
            if (!hasText(request.getReviewComment())) {
                throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "驳回任务时必须填写驳回原因");
            }
        }

        Task before = TaskSnapshots.copy(task);
        task.setStatus(targetStatus);
        Task saved = taskRepository.save(task);
        taskHistoryRecorder.recordUpdate(before, saved, actorUsername);

        // COMPLETED 时：归集任务交付物到项目文档（幂等 + 批量）
        if (targetStatus == Task.Status.COMPLETED) {
            deliverableCollector.collect(projectId, taskId);
        }

        return toTaskView(saved);
    }

    ProjectTaskViewDTO createTaskFromDraft(ProjectScoreDraft draft) {
        Task task = Task.builder()
                .projectId(draft.getProjectId())
                .title(draft.getGeneratedTaskTitle())
                .description(draft.getGeneratedTaskDescription())
                .assigneeId(draft.getAssigneeId())
                .priority(resolvePriority(draft.getScoreValueText()))
                .status(Task.Status.TODO)
                .dueDate(draft.getDueDate())
                .build();
        Task saved = taskRepository.save(task);
        return toTaskView(saved, draft.getAssigneeName());
    }

    ProjectTaskViewDTO toTaskView(Task task) {
        return toTaskView(task, null);
    }

    private ProjectTaskViewDTO toTaskView(Task task, String fallbackAssigneeName) {
        String assigneeName = resolveDisplayName(task.getAssigneeId(), fallbackAssigneeName);
        return ProjectTaskViewDTO.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .name(task.getTitle())
                .description(task.getDescription())
                .content(task.getContent())
                .extendedFields(deserializeExtendedFields(task))
                .assigneeId(task.getAssigneeId())
                .assigneeDeptCode(task.getAssigneeDeptCode())
                .assigneeRoleCode(task.getAssigneeRoleCode())
                .owner(assigneeName)
                .assignee(assigneeName)
                .department(defaultString(task.getAssigneeDeptName(), "未配置部门"))
                .roleName(defaultString(task.getAssigneeRoleName(), "未配置角色"))
                .status(mapStatus(task.getStatus()))
                .priority(mapPriority(task.getPriority()))
                .dueDate(task.getDueDate() != null ? task.getDueDate().format(DISPLAY_DATE) : "")
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .completionNotes(task.getCompletionNotes())
                .build();
    }

    private User resolveAssignee(Long assigneeId) {
        if (assigneeId == null) {
            return null;
        }
        return userRepository.findById(assigneeId).orElse(null);
    }

    private User resolveAssignee(Long assigneeId, String creatorUsername) {
        User explicitAssignee = resolveAssignee(assigneeId);
        if (explicitAssignee != null || assigneeId != null || !hasText(creatorUsername)) {
            return explicitAssignee;
        }
        return userRepository.findByUsername(creatorUsername).orElse(null);
    }

    private String resolveDisplayName(Long userId, String fallback) {
        if (userId != null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getFullName() != null && !user.getFullName().isBlank()) {
                return user.getFullName();
            }
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback.trim();
        }
        return "未分配";
    }

    private Task.Priority resolvePriority(String scoreValueText) {
        String scoreValue = defaultString(scoreValueText, "");
        if (scoreValue.contains("10") || scoreValue.contains("最高")) {
            return Task.Priority.HIGH;
        }
        return Task.Priority.MEDIUM;
    }

    private String mapStatus(Task.Status status) {
        if (status == null) {
            return "todo";
        }
        return switch (status) {
            case TODO -> "todo";
            case REVIEW -> "review";
            case COMPLETED -> "done";
        };
    }

    private String mapPriority(Task.Priority priority) {
        if (priority == null) {
            return "medium";
        }
        return switch (priority) {
            case LOW -> "low";
            case MEDIUM -> "medium";
            case HIGH -> "high";
            case URGENT -> "urgent";
        };
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String defaultString(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized != null ? normalized : fallback;
    }

    private String serializeExtendedFields(Map<String, Object> extendedFields) {
        if (extendedFields == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extendedFields);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("扩展字段格式无效", e);
        }
    }

    private Map<String, Object> deserializeExtendedFields(Task task) {
        if (!hasText(task.getExtendedFieldsJson())) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(task.getExtendedFieldsJson(), EXTENDED_FIELDS_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Task.Priority toEntityPriority(ProjectTaskCreateRequest.Priority priority) {
        if (priority == null) {
            return null;
        }
        return Task.Priority.valueOf(priority.name());
    }

    private Task.Status toEntityStatus(ProjectTaskStatusUpdateRequest.Status status) {
        if (status == null) {
            return null;
        }
        return Task.Status.valueOf(status.name());
    }
}
