// Input: task id, comment commands and authenticated username
// Output: guarded task activity timeline and persisted comments
// Pos: Service/任务动态编排层
package com.xiyu.bid.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.exception.ResourceNotFoundException;
import com.xiyu.bid.mention.dto.CreateMentionRequest;
import com.xiyu.bid.mention.service.MentionApplicationService;
import com.xiyu.bid.repository.TaskRepository;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.service.ProjectAccessScopeService;
import com.xiyu.bid.task.core.TaskProjectVisibilityPolicy;
import com.xiyu.bid.task.dto.TaskActivityDTO;
import com.xiyu.bid.task.dto.TaskCommentCreateRequest;
import com.xiyu.bid.task.entity.TaskComment;
import com.xiyu.bid.task.entity.TaskHistory;
import com.xiyu.bid.task.repository.TaskCommentRepository;
import com.xiyu.bid.task.repository.TaskHistoryRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class TaskActivityService {

    private static final TypeReference<Map<String, Object>> SNAPSHOT_TYPE = new TypeReference<>() {};

    private final TaskRepository taskRepository;
    private final TaskCommentRepository commentRepository;
    private final TaskHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ProjectAccessScopeService projectAccessScopeService;
    private final MentionApplicationService mentionService;
    private final ObjectMapper objectMapper;

    public TaskActivityService(
            TaskRepository taskRepository,
            TaskCommentRepository commentRepository,
            TaskHistoryRepository historyRepository,
            UserRepository userRepository,
            ProjectAccessScopeService projectAccessScopeService,
            MentionApplicationService mentionService,
            ObjectMapper objectMapper
    ) {
        this.taskRepository = taskRepository;
        this.commentRepository = commentRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.projectAccessScopeService = projectAccessScopeService;
        this.mentionService = mentionService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TaskActivityDTO createComment(Long taskId, TaskCommentCreateRequest request, String username) {
        Task task = requireAccessibleTask(taskId);
        User actor = resolveUser(username);
        String content = normalizeContent(request.content());
        TaskComment saved = commentRepository.save(TaskComment.builder()
                .taskId(task.getId())
                .authorUserId(actor.getId())
                .content(content)
                .build());
        mentionService.createMention(new CreateMentionRequest(
                content,
                "TASK",
                task.getId(),
                "任务评论提及：" + task.getTitle()
        ), actor.getId());
        return commentToActivity(saved, actor.getFullName());
    }

    public List<TaskActivityDTO> getActivity(Long taskId) {
        requireAccessibleTask(taskId);
        List<TaskActivityDTO> comments = commentRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                .stream()
                .map(comment -> commentToActivity(comment, displayName(comment.getAuthorUserId())))
                .toList();
        List<TaskActivityDTO> histories = historyRepository.findByTaskIdAndArchivedAtIsNullOrderByCreatedAtDesc(taskId)
                .stream()
                .map(this::historyToActivity)
                .toList();
        return java.util.stream.Stream.concat(comments.stream(), histories.stream())
                .sorted(Comparator.comparing(TaskActivityDTO::createdAt, TaskActivityService::compareNullableDesc))
                .toList();
    }

    private Task requireAccessibleTask(Long taskId) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResourceNotFoundException("Task", String.valueOf(taskId)));
        if (!TaskProjectVisibilityPolicy.canAccessProject(
                task.getProjectId(),
                projectAccessScopeService.getAllowedProjectIdsForCurrentUser()
        )) {
            throw new AccessDeniedException("权限不足，无法访问该项目任务动态");
        }
        return task;
    }

    private User resolveUser(String username) {
        if (username == null || username.isBlank()) {
            throw new UsernameNotFoundException("Authenticated username is blank");
        }
        return userRepository.findByUsername(username.trim())
                .orElseThrow(() -> new UsernameNotFoundException("Authenticated user not found: " + username));
    }

    private String displayName(Long userId) {
        if (userId == null) {
            return "系统";
        }
        return userRepository.findById(userId)
                .map(User::getFullName)
                .filter(name -> !name.isBlank())
                .orElse("系统");
    }

    private String normalizeContent(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("评论内容不能为空");
        }
        return normalized;
    }

    private TaskActivityDTO commentToActivity(TaskComment comment, String actorName) {
        return new TaskActivityDTO(
                "COMMENT",
                comment.getId(),
                comment.getTaskId(),
                comment.getAuthorUserId(),
                actorName,
                comment.getContent(),
                null,
                null,
                comment.getCreatedAt()
        );
    }

    private TaskActivityDTO historyToActivity(TaskHistory history) {
        return new TaskActivityDTO(
                "HISTORY",
                history.getId(),
                history.getTaskId(),
                history.getActorUserId(),
                displayName(history.getActorUserId()),
                null,
                history.getAction(),
                parseSnapshot(history.getSnapshotJson()),
                history.getCreatedAt()
        );
    }

    private Map<String, Object> parseSnapshot(String snapshotJson) {
        if (snapshotJson == null || snapshotJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(snapshotJson, SNAPSHOT_TYPE);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

    private static int compareNullableDesc(LocalDateTime a, LocalDateTime b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }
}
