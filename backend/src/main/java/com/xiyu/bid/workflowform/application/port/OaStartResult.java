package com.xiyu.bid.workflowform.application.port;

public record OaStartResult(boolean success, String oaInstanceId, String errorMessage) {
}
