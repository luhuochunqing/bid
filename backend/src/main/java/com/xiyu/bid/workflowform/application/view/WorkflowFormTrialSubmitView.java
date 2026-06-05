package com.xiyu.bid.workflowform.application.view;

import java.util.Map;

public record WorkflowFormTrialSubmitView(
        boolean oaStarted,
        String oaInstanceId,
        String errorMessage,
        Map<String, Object> payload
) {
}
