package com.xiyu.bid.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating a task deliverable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDeliverableCreateRequest {

    /** Deliverable name. */
    @NotBlank(message = "交付物名称不能为空")
    private String name;

    /** Deliverable type enum string. */
    @NotNull(message = "交付物类型不能为空")
    private String deliverableType;

    /** File size display string. */
    private String size;

    /** MIME type of uploaded file. */
    private String fileType;

    /** Stored download URL returned by the real project document upload path. */
    private String url;
}
