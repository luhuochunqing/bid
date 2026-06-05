package com.xiyu.bid.workflowform.application.view;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;

public record WorkflowFormInstanceView(
        Long id,
        FormBusinessType businessType,
        String templateCode,
        Long projectId,
        WorkflowFormStatus status,
        String oaInstanceId,
        String businessApplyError
) {
}
