package com.xiyu.bid.workflowform.infrastructure.oa;

import com.xiyu.bid.docinsight.application.DocumentStorage;
import com.xiyu.bid.docinsight.application.StoredDocument;
import com.xiyu.bid.workflowform.application.port.StoredWorkflowFormAttachment;
import com.xiyu.bid.workflowform.application.port.WorkflowFormAttachmentStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DocInsightWorkflowFormAttachmentStorage implements WorkflowFormAttachmentStorage {

    private static final String CATEGORY = "workflow-form-attachments";

    private final DocumentStorage documentStorage;

    @Override
    public StoredWorkflowFormAttachment store(String ownerKey, String fileName, String contentType, byte[] content) {
        StoredDocument stored = documentStorage.store(CATEGORY, ownerKey, fileName, contentType, content);
        return new StoredWorkflowFormAttachment(stored.fileUrl(), stored.fileUrl());
    }
}
