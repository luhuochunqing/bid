package com.xiyu.bid.workflowform.application.command;

import com.xiyu.bid.workflowform.domain.OaApprovalStatus;

public record OaCallbackCommand(String oaInstanceId, OaApprovalStatus status, String operatorName, String comment, String eventId) {
}
