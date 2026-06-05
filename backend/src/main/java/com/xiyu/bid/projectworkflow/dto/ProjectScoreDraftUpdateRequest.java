package com.xiyu.bid.projectworkflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectScoreDraftUpdateRequest {
    public enum Status {
        DRAFT,
        READY,
        SKIPPED,
        GENERATED
    }

    private Long assigneeId;
    private String assigneeName;
    private LocalDateTime dueDate;
    private String generatedTaskTitle;
    private String generatedTaskDescription;
    private Status status;
    private String skipReason;
}
