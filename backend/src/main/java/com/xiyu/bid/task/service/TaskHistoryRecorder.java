// Input: before/after Task snapshots and actor username
// Output: task_history row plus EntityChangedEvent for subscribers
// Pos: Component/任务历史记录外壳
package com.xiyu.bid.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.changetracking.event.EntityChangedEvent;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.entity.User;
import com.xiyu.bid.repository.UserRepository;
import com.xiyu.bid.task.entity.TaskHistory;
import com.xiyu.bid.task.repository.TaskHistoryRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class TaskHistoryRecorder {

    private static final String ACTION_UPDATE = "UPDATE";

    private final TaskHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    public TaskHistoryRecorder(
            TaskHistoryRepository historyRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper,
            ApplicationEventPublisher eventPublisher
    ) {
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void recordUpdate(Task before, Task after, String actorUsername) {
        if (before == null || after == null || after.getId() == null) {
            return;
        }
        Long actorUserId = resolveActorUserId(actorUsername);
        Map<String, Object> beforeSnapshot = snapshot(before);
        Map<String, Object> afterSnapshot = snapshot(after);
        historyRepository.save(TaskHistory.builder()
                .taskId(after.getId())
                .actorUserId(actorUserId)
                .action(ACTION_UPDATE)
                .snapshotJson(serialize(afterSnapshot))
                .build());
        if (actorUserId != null) {
            eventPublisher.publishEvent(new EntityChangedEvent(
                    "TASK",
                    after.getId(),
                    actorUserId,
                    beforeSnapshot,
                    afterSnapshot,
                    after.getTitle(),
                    Map.of("projectId", after.getProjectId())
            ));
        }
    }

    @Transactional
    public int archiveBefore(LocalDateTime cutoff) {
        return historyRepository.archiveBefore(cutoff, LocalDateTime.now());
    }

    private Long resolveActorUserId(String actorUsername) {
        if (actorUsername == null || actorUsername.isBlank()) {
            return null;
        }
        return userRepository.findByUsername(actorUsername.trim()).map(User::getId).orElse(null);
    }

    private String serialize(Map<String, Object> snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务历史快照序列化失败", e);
        }
    }

    static Map<String, Object> snapshot(Task task) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", task.getId());
        out.put("projectId", task.getProjectId());
        out.put("title", task.getTitle());
        out.put("description", task.getDescription());
        out.put("content", task.getContent());
        out.put("assigneeId", task.getAssigneeId());
        out.put("assigneeDeptCode", task.getAssigneeDeptCode());
        out.put("assigneeDeptName", task.getAssigneeDeptName());
        out.put("assigneeRoleCode", task.getAssigneeRoleCode());
        out.put("assigneeRoleName", task.getAssigneeRoleName());
        out.put("status", task.getStatus() == null ? null : task.getStatus().name());
        out.put("priority", task.getPriority() == null ? null : task.getPriority().name());
        out.put("dueDate", task.getDueDate() == null ? null : task.getDueDate().toString());
        out.put("extendedFieldsJson", task.getExtendedFieldsJson());
        return out;
    }
}
