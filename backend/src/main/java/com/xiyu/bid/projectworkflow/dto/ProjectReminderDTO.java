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
public class ProjectReminderDTO {

    private Long id;
    private Long projectId;
    private String title;
    private String message;
    private LocalDateTime remindAt;
    private Long createdBy;
    private String createdByName;
    private String recipient;
    private LocalDateTime createdAt;
}
