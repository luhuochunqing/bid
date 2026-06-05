package com.xiyu.bid.task.dto;

import com.xiyu.bid.entity.Task;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 任务数据传输对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDTO {

    private Long id;
    private Long projectId;
    private String title;
    private String description;
    /**
     * Markdown rich content (raw text, sanitized at render time by the frontend).
     * Persisted to {@code tasks.content TEXT} (V102, up to ~64KB).
     */
    private String content;
    private Long assigneeId;
    private String assigneeDeptCode;
    private String assigneeDeptName;
    private String assigneeRoleCode;
    private String assigneeRoleName;
    private Task.Status status;
    private Task.Priority priority;
    private LocalDateTime dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    /**
     * 动态扩展字段（由 task_extended_field 定义、在任务粒度存储）。
     * 在实体上以 JSON 字符串形式保存于 {@code tasks.extended_fields_json}，
     * 在 DTO 层暴露为 {@code Map<String, Object>} 便于前端直接使用。
     * 当底层 JSON 为空时，{@code toDTO} 返回空 Map 而非 {@code null}。
     */
    private Map<String, Object> extendedFields;
}
