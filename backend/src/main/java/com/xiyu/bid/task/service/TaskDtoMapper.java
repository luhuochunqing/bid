// Input: Task entity and extended field JSON
// Output: TaskDTO and JSON payload conversion
// Pos: Mapper/任务 DTO 转换
package com.xiyu.bid.task.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.entity.Task;
import com.xiyu.bid.projectworkflow.dto.ProjectDocumentDTO;
import com.xiyu.bid.projectworkflow.entity.ProjectDocument;
import com.xiyu.bid.projectworkflow.repository.ProjectDocumentRepository;
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
    private final ProjectDocumentRepository projectDocumentRepository;

    public TaskDtoMapper(ObjectMapper objectMapper, ProjectDocumentRepository projectDocumentRepository) {
        this.objectMapper = objectMapper;
        this.projectDocumentRepository = projectDocumentRepository;
    }

    public List<TaskDTO> toDTOs(List<Task> tasks) {
        return tasks.stream().map(this::toDTO).toList();
    }

    public List<TaskDTO> toDTOs(List<Task> tasks, Map<Long, String> assigneeNames) {
        return tasks.stream().map(t -> toDTO(t, assigneeNames.get(t.getAssigneeId()))).toList();
    }

    public TaskDTO toDTO(Task task) {
        return toDTO(task, null);
    }

    public TaskDTO toDTO(Task task, String assigneeName) {
        return TaskDTO.builder()
                .id(task.getId())
                .projectId(task.getProjectId())
                .title(task.getTitle())
                .description(task.getDescription())
                .content(task.getContent())
                .assigneeId(task.getAssigneeId())
                .assigneeName(assigneeName)
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
                .attachments(loadTaskAttachments(task.getId()))
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

    private List<ProjectDocumentDTO> loadTaskAttachments(Long taskId) {
        if (taskId == null || projectDocumentRepository == null) {
            return Collections.emptyList();
        }
        List<ProjectDocument> documents = projectDocumentRepository
                .findByLinkedEntityTypeAndLinkedEntityIdOrderByCreatedAtDesc("TASK", taskId);
        if (documents == null || documents.isEmpty()) {
            return Collections.emptyList();
        }
        return documents.stream()
                .filter(doc -> "TASK_ATTACHMENT".equals(doc.getDocumentCategory()))
                .map(this::toProjectDocumentDTO)
                .toList();
    }

    private ProjectDocumentDTO toProjectDocumentDTO(ProjectDocument doc) {
        return ProjectDocumentDTO.builder()
                .id(doc.getId())
                .projectId(doc.getProjectId())
                .name(doc.getName())
                .size(doc.getSize())
                .fileType(doc.getFileType())
                .documentCategory(doc.getDocumentCategory())
                .linkedEntityType(doc.getLinkedEntityType())
                .linkedEntityId(doc.getLinkedEntityId())
                .fileUrl(doc.getFileUrl())
                .uploaderId(doc.getUploaderId())
                .uploader(doc.getUploaderName())
                .createdAt(doc.getCreatedAt())
                .build();
    }
}
