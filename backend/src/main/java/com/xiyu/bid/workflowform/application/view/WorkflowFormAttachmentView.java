package com.xiyu.bid.workflowform.application.view;

public record WorkflowFormAttachmentView(
        String fileName,
        String fileUrl,
        String storagePath,
        String contentType,
        long size
) {
}
