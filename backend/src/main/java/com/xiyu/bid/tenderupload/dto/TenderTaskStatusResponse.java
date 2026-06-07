package com.xiyu.bid.tenderupload.dto;

import com.xiyu.bid.tenderupload.entity.TenderTaskStatus;
import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class TenderTaskStatusResponse {
    private final Long taskId;
    private final Long fileId;
    private final TenderTaskStatus status;
    private final Integer attempts;
    private final Integer priority;
    private final Long queuePosition;
    private final LocalDateTime estimatedStartAt;
    private final String errorCode;
    private final String errorMessage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;
}
