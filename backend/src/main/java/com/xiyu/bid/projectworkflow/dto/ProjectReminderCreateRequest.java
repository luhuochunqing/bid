package com.xiyu.bid.projectworkflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectReminderCreateRequest {

    @NotBlank(message = "提醒标题不能为空")
    private String title;

    private String message;

    @NotNull(message = "提醒时间不能为空")
    private LocalDateTime remindAt;

    private Long createdBy;

    private String createdByName;

    private String recipient;
}
