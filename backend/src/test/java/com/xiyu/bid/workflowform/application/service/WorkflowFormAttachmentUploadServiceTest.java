package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.WorkflowFormAttachmentUploadCommand;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateStore;
import com.xiyu.bid.workflowform.application.port.StoredWorkflowFormAttachment;
import com.xiyu.bid.workflowform.application.port.WorkflowFormAttachmentStorage;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFormAttachmentUploadServiceTest {

    @Test
    void upload_stores_attachment_and_returns_structured_metadata() {
        CapturingStorage storage = new CapturingStorage();
        WorkflowFormAttachmentUploadService service = new WorkflowFormAttachmentUploadService(
                storage,
                new NoopWorkflowFormAccessGuard(),
                templateStore(),
                new WorkflowFormSchemaAssembler()
        );

        var view = service.upload(new WorkflowFormAttachmentUploadCommand(
                "QUALIFICATION_BORROW",
                "supportingFiles",
                10L,
                "授权书.pdf",
                "application/pdf",
                "pdf-content".getBytes()
        ));

        assertThat(storage.ownerKey).isEqualTo("10");
        assertThat(storage.fileName).isEqualTo("授权书.pdf");
        assertThat(storage.contentType).isEqualTo("application/pdf");
        assertThat(storage.content).isEqualTo("pdf-content".getBytes());
        assertThat(view.fileName()).isEqualTo("授权书.pdf");
        assertThat(view.fileUrl()).isEqualTo("doc-insight://workflow-form-attachments/10/stored.pdf");
        assertThat(view.storagePath()).isEqualTo("doc-insight://workflow-form-attachments/10/stored.pdf");
        assertThat(view.contentType()).isEqualTo("application/pdf");
        assertThat(view.size()).isEqualTo(11L);
    }

    @Test
    void upload_rejects_non_attachment_schema_field() {
        WorkflowFormAttachmentUploadService service = new WorkflowFormAttachmentUploadService(
                new CapturingStorage(),
                new NoopWorkflowFormAccessGuard(),
                templateStore(),
                new WorkflowFormSchemaAssembler()
        );

        assertThatThrownBy(() -> service.upload(new WorkflowFormAttachmentUploadCommand(
                "QUALIFICATION_BORROW",
                "borrower",
                10L,
                "授权书.pdf",
                "application/pdf",
                "pdf-content".getBytes()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未找到可上传的附件字段");
    }

    @Test
    void upload_rejects_executable_attachment() {
        WorkflowFormAttachmentUploadService service = new WorkflowFormAttachmentUploadService(
                new CapturingStorage(),
                new NoopWorkflowFormAccessGuard(),
                templateStore(),
                new WorkflowFormSchemaAssembler()
        );

        assertThatThrownBy(() -> service.upload(new WorkflowFormAttachmentUploadCommand(
                "QUALIFICATION_BORROW",
                "supportingFiles",
                10L,
                "run.exe",
                "application/x-msdownload",
                "exe-content".getBytes()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不支持上传可执行附件");
    }

    private static WorkflowFormTemplateStore templateStore() {
        return templateCode -> Optional.of(new WorkflowFormTemplateRecord(
                templateCode,
                FormBusinessType.QUALIFICATION_BORROW,
                1,
                Map.of("fields", List.of(
                        Map.of("key", "borrower", "label", "借用人", "type", "text", "required", true),
                        Map.of("key", "supportingFiles", "label", "附件", "type", "attachment", "required", true)
                ))
        ));
    }

    static class CapturingStorage implements WorkflowFormAttachmentStorage {
        String ownerKey;
        String fileName;
        String contentType;
        byte[] content;

        @Override
        public StoredWorkflowFormAttachment store(String ownerKey, String fileName, String contentType, byte[] content) {
            this.ownerKey = ownerKey;
            this.fileName = fileName;
            this.contentType = contentType;
            this.content = content;
            return new StoredWorkflowFormAttachment(
                    "doc-insight://workflow-form-attachments/10/stored.pdf",
                    "doc-insight://workflow-form-attachments/10/stored.pdf"
            );
        }
    }
}
