package com.xiyu.bid.workflowform.dto;

import com.xiyu.bid.workflowform.domain.OaApprovalStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record OaCallbackRequest(
        @NotBlank String oaInstanceId,
        @NotNull OaApprovalStatus status,
        String operatorName,
        String comment,
        @NotBlank String timestamp,
        @NotBlank String nonce,
        @NotBlank String signature,
        @NotBlank String eventId
) {
}
