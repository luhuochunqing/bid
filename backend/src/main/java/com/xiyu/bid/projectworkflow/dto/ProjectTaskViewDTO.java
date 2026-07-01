package com.xiyu.bid.projectworkflow.dto;

import com.xiyu.bid.task.dto.TaskDeliverableDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTaskViewDTO {

    private Long id;
    private Long projectId;
    private String name;
    private String description;
    /**
     * Markdown rich content persisted to {@code tasks.content TEXT} (V102, up to ~64KB).
     * Mirrors the {@code content} field on {@link com.xiyu.bid.task.dto.TaskDTO} so the
     * task drawer can round-trip Markdown across page reloads.
     */
    private String content;
    /**
     * Admin-defined extended field key/value pairs persisted to
     * {@code tasks.extended_fields_json TEXT} (V103). Mirrors
     * {@link com.xiyu.bid.task.dto.TaskDTO#getExtendedFields()} so TaskForm
     * can prefill custom field values when reopening a task post-reload.
     */
    private Map<String, Object> extendedFields;
    private Long assigneeId;
    private String assigneeDeptCode;
    private String assigneeRoleCode;
    private String owner;
    private String assignee;
    private String department;
    private String roleName;
    private String status;
    private String priority;
    private String dueDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Integer deliverableCount;
    /**
     * CO-370 fix: 完成情况说明（Task 实体 completion_notes 字段）。
     * 前端在状态流转后用后端返回覆盖内存中的 task 对象，缺失此字段会导致已填写的说明被空值覆盖。
     */
    private String completionNotes;
    /**
     * CO-460 治本：任务交付物列表（来自 task_deliverables 表），对齐独立任务 TaskDTO.deliverables。
     * 此前 toTaskView 从不加载交付物，审核（REVIEW→通过/驳回）返回的 DTO 不含 deliverables，
     * 前端 Object.assign 覆盖原引用导致交付物附件丢失。
     */
    private List<TaskDeliverableDTO> deliverables;
    /**
     * CO-460 治本：任务附件列表（来自 project_documents 表，documentCategory=TASK_ATTACHMENT），
     * 对齐独立任务 TaskDTO.attachments。
     */
    private List<ProjectDocumentDTO> attachments;
}
