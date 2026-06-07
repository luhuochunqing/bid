package com.xiyu.bid.workflowform.application.port;

public interface WorkflowFormAttachmentStorage {
    StoredWorkflowFormAttachment store(String ownerKey, String fileName, String contentType, byte[] content);
}
