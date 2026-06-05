package com.xiyu.bid.projectworkflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTaskCreateRequest {

    public enum Priority {
        LOW,
        MEDIUM,
        HIGH,
        URGENT
    }

    @NotBlank(message = "任务名称不能为空")
    private String title;

    private String description;

    private String content;

    /**
     * Admin-defined extended field key/value pairs. Persisted as JSON to
     * {@code tasks.extended_fields_json TEXT} (V103). Schema lives in
     * {@code task_extended_field}.
     */
    private Map<String, Object> extendedFields;

    private Long assigneeId;

    private String assigneeName;

    private String assigneeDeptCode;

    private String assigneeDeptName;

    private String assigneeRoleCode;

    private String assigneeRoleName;

    @NotNull(message = "任务优先级不能为空")
    private Priority priority;

    private LocalDateTime dueDate;
}
