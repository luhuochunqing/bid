package com.xiyu.bid.projectworkflow.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectTaskStatusUpdateRequest {

    public enum Status {
        TODO,
        REVIEW,
        COMPLETED
    }

    @NotNull(message = "任务状态不能为空")
    private Status status;

    private String reviewComment;
}
