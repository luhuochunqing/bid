package com.xiyu.bid.task.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for a task deliverable returned to clients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskDeliverableDTO {

    /** Unique identifier. */
    private Long id;

    /** Owning task id. */
    private Long taskId;

    /** Deliverable display name. */
    private String name;

    /** Deliverable type enum string. */
    private String deliverableType;

    /** File size display string. */
    private String size;

    /** MIME type. */
    private String fileType;

    /** Download URL (v1 may be null). */
    private String url;

    /** Version number starting from 1. */
    private Integer version;

    /** Uploader user id. */
    private Long uploaderId;

    /** Uploader display name. */
    private String uploaderName;

    /** Creation timestamp. */
    private LocalDateTime createdAt;
}
