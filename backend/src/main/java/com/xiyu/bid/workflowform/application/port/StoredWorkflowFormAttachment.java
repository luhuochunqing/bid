package com.xiyu.bid.workflowform.application.port;

public record StoredWorkflowFormAttachment(
        String fileUrl,
        String storagePath
) {
}
