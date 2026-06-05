// Input: Task entity and extended field JSON
// Output: TaskDTO and JSON payload conversion
// Pos: Mapper/任务 DTO 转换
package com.xiyu.bid.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.task.dto.TaskDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class TaskDtoMapper {

    private static final Logger log = LoggerFactory.getLogger(TaskDtoMapper.class);
    private static final TypeReference<Map<String, Object>> EXTENDED_FIELDS_TYPE =
            new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public TaskDtoMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TaskDTO> toDTOs(List<Task> tasks) {
        return tasks.stream().map(this::toDTO).toList();
    }

    public TaskDTO toDTO(Task task) {
        return TaskDTO.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .content(task.getContent())
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
                .extendedFields(deserializeExtendedFields(task))
                .build();
    }

    public String serializeExtendedFields(Map<String, Object> extendedFields) {
        if (extendedFields == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(extendedFields);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("扩展字段序列化失败: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> deserializeExtendedFields(Task task) {
        String json = task.getExtendedFieldsJson();
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, EXTENDED_FIELDS_TYPE);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse extendedFieldsJson for task {}: {}", task.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }
}
