package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.WorkflowFormAttachmentUploadCommand;
import com.xiyu.bid.workflowform.application.port.StoredWorkflowFormAttachment;
import com.xiyu.bid.workflowform.application.port.WorkflowFormAttachmentStorage;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateStore;
import com.xiyu.bid.workflowform.application.view.WorkflowFormAttachmentView;
import com.xiyu.bid.workflowform.domain.FormFieldType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkflowFormAttachmentUploadService {

    private static final int MAX_ATTACHMENT_BYTES = 30 * 1024 * 1024;

    private final WorkflowFormAttachmentStorage storage;
    private final WorkflowFormAccessGuard accessGuard;
    private final WorkflowFormTemplateStore templateStore;
    private final WorkflowFormSchemaAssembler schemaAssembler;

    public WorkflowFormAttachmentView upload(WorkflowFormAttachmentUploadCommand command) {
        accessGuard.assertCanAccessProject(command.projectId());
        requireAttachmentField(command.templateCode(), command.fieldKey());
        requireFileContent(command.content());
        String fileName = normalizeFileName(command.fileName());
        requireSafeFile(fileName, command.contentType(), command.content().length);
        String contentType = normalizeContentType(command.contentType());
        StoredWorkflowFormAttachment stored = storage.store(ownerKey(command.projectId()), fileName, contentType, command.content());
        return new WorkflowFormAttachmentView(fileName, stored.fileUrl(), stored.storagePath(), contentType, command.content().length);
    }

    private void requireAttachmentField(String templateCode, String fieldKey) {
        if (templateCode == null || templateCode.isBlank() || fieldKey == null || fieldKey.isBlank()) {
            throw new IllegalArgumentException("附件字段配置不能为空");
        }
        boolean attachmentField = templateStore.findActiveByCode(templateCode)
                .map(schemaAssembler::toSchema)
                .stream()
                .flatMap(schema -> schema.fields().stream())
                .anyMatch(field -> field.key().equals(fieldKey) && field.type() == FormFieldType.ATTACHMENT);
        if (!attachmentField) {
            throw new IllegalArgumentException("未找到可上传的附件字段: " + fieldKey);
        }
    }

    private void requireFileContent(byte[] content) {
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("附件内容不能为空");
        }
    }

    private void requireSafeFile(String fileName, String contentType, int size) {
        if (size > MAX_ATTACHMENT_BYTES) {
            throw new IllegalArgumentException("附件大小不能超过 30MB");
        }
        String lowerName = fileName.toLowerCase();
        String normalizedContentType = normalizeContentType(contentType).toLowerCase();
        if (lowerName.endsWith(".exe") || lowerName.endsWith(".sh") || lowerName.endsWith(".bat")
                || normalizedContentType.contains("x-msdownload")
                || normalizedContentType.contains("x-sh")) {
            throw new IllegalArgumentException("不支持上传可执行附件");
        }
    }

    private String normalizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "attachment";
        }
        return fileName.trim();
    }

    private String normalizeContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return "application/octet-stream";
        }
        return contentType.trim();
    }

    private String ownerKey(Long projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("附件上传必须关联项目");
        }
        return String.valueOf(projectId);
    }
}
