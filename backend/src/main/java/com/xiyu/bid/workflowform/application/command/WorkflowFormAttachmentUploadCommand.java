package com.xiyu.bid.workflowform.application.command;

public record WorkflowFormAttachmentUploadCommand(
        String templateCode,
        String fieldKey,
        Long projectId,
        String fileName,
        String contentType,
        byte[] content
) {
}
