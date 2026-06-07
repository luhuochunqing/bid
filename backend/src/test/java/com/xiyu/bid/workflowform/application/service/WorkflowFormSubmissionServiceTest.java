package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormSubmitCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;
import com.xiyu.bid.workflowform.application.port.InMemoryWorkflowFormAdminStore;
import com.xiyu.bid.workflowform.application.port.OaAttachmentPayload;
import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.application.port.OaStartResult;
import com.xiyu.bid.workflowform.application.port.OaWorkflowGateway;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceStore;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowFormSubmissionServiceTest {

    @Test
    void submit_qualification_borrow_form_starts_oa_without_applying_borrow() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        InMemoryWorkflowFormAdminStore adminStore = publishedAdminStore();
        CapturingOaWorkflowGateway gateway = new CapturingOaWorkflowGateway();
        WorkflowFormSubmissionService service = service(store, adminStore, gateway);

        var view = service.submit(new WorkflowFormSubmitCommand(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                10L,
                "小王",
                values()
        ));

        assertThat(view.id()).isEqualTo(1L);
        assertThat(view.status()).isEqualTo(WorkflowFormStatus.OA_APPROVING);
        assertThat(view.oaInstanceId()).isEqualTo("OA-1");
        assertThat(store.findById(1L).orElseThrow().businessApplied()).isFalse();
        assertThat(gateway.lastCommand.workflowCode()).isEqualTo("WF_QUALIFICATION_BORROW");
        assertThat(gateway.lastCommand.templateCode()).isEqualTo("QUALIFICATION_BORROW");
        assertThat(gateway.lastCommand.trial()).isFalse();
        assertThat(gateway.lastCommand.mappedPayload()).containsEntry("trial", false);
        assertThat((Map<String, Object>) gateway.lastCommand.mappedPayload().get("mainFields"))
                .containsEntry("field_qualification", "1001");
        assertThat((Map<String, Object>) store.findById(1L).orElseThrow().oaPayload().get("mainFields"))
                .containsEntry("field_qualification", "1001");
    }

    @Test
    void oa_start_failure_preserves_local_instance_for_retry() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        InMemoryWorkflowFormAdminStore adminStore = publishedAdminStore();
        CapturingOaWorkflowGateway gateway = new CapturingOaWorkflowGateway();
        gateway.result = new OaStartResult(false, null, "OA 暂不可用");
        WorkflowFormSubmissionService service = service(store, adminStore, gateway);

        var view = service.submit(new WorkflowFormSubmitCommand(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                10L,
                "小王",
                values()
        ));

        assertThat(view.status()).isEqualTo(WorkflowFormStatus.OA_FAILED);
        assertThat(store.findById(1L).orElseThrow().businessApplyError()).isEqualTo("OA 暂不可用");
    }

    @Test
    void submit_keeps_attachment_payload_structured_for_oa_command() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        InMemoryWorkflowFormAdminStore adminStore = publishedAdminStore();
        CapturingOaWorkflowGateway gateway = new CapturingOaWorkflowGateway();
        WorkflowFormSubmissionService service = service(store, adminStore, gateway);
        Map<String, Object> values = values();
        values.put("supportingFiles", java.util.List.of(Map.of(
                "fileName", "授权书.pdf",
                "fileUrl", "doc-insight://workflow-form-attachments/10/a.pdf",
                "storagePath", "doc-insight://workflow-form-attachments/10/a.pdf",
                "contentType", "application/pdf",
                "size", 2048L
        )));

        service.submit(new WorkflowFormSubmitCommand(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                10L,
                "小王",
                values
        ));

        Object attachmentValue = gateway.lastCommand.formData().get("supportingFiles");
        assertThat(attachmentValue).asList().singleElement().isInstanceOf(OaAttachmentPayload.class);
        OaAttachmentPayload payload = (OaAttachmentPayload) ((java.util.List<?>) attachmentValue).getFirst();
        assertThat(payload.fileName()).isEqualTo("授权书.pdf");
        assertThat(payload.fileUrl()).isEqualTo("doc-insight://workflow-form-attachments/10/a.pdf");
        assertThat(payload.storagePath()).isEqualTo("doc-insight://workflow-form-attachments/10/a.pdf");
        assertThat(payload.contentType()).isEqualTo("application/pdf");
        assertThat(payload.size()).isEqualTo(2048L);
    }

    @Test
    void submit_rejects_missing_required_attachment_from_active_template_schema() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        InMemoryWorkflowFormAdminStore adminStore = publishedAdminStore();
        CapturingOaWorkflowGateway gateway = new CapturingOaWorkflowGateway();
        WorkflowFormSubmissionService service = service(store, adminStore, gateway);

        assertThatThrownBy(() -> service.submit(new WorkflowFormSubmitCommand(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                10L,
                "小王",
                valuesWithoutAttachment()
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("请上传附件");

        assertThat(gateway.lastCommand).isNull();
    }

    @Test
    void submit_rejects_attachment_reference_not_uploaded_for_current_project() {
        InMemoryWorkflowFormInstanceStore store = new InMemoryWorkflowFormInstanceStore();
        InMemoryWorkflowFormAdminStore adminStore = publishedAdminStore();
        CapturingOaWorkflowGateway gateway = new CapturingOaWorkflowGateway();
        WorkflowFormSubmissionService service = service(store, adminStore, gateway);
        Map<String, Object> values = valuesWithoutAttachment();
        values.put("supportingFiles", java.util.List.of(Map.of(
                "fileName", "授权书.pdf",
                "fileUrl", "doc-insight://workflow-form-attachments/99/a.pdf"
        )));

        assertThatThrownBy(() -> service.submit(new WorkflowFormSubmitCommand(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                10L,
                "小王",
                values
        ))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("附件必须通过当前项目的流程表单上传入口上传");

        assertThat(gateway.lastCommand).isNull();
    }

    private static WorkflowFormSubmissionService service(
            WorkflowFormInstanceStore store,
            InMemoryWorkflowFormAdminStore adminStore,
            CapturingOaWorkflowGateway gateway) {
        return new WorkflowFormSubmissionService(
                store,
                adminStore,
                new WorkflowFormSchemaAssembler(),
                gateway,
                new NoopWorkflowFormAccessGuard(),
                TestTransactionTemplates.immediate()
        );
    }

    private static InMemoryWorkflowFormAdminStore publishedAdminStore() {
        InMemoryWorkflowFormAdminStore store = new InMemoryWorkflowFormAdminStore();
        store.saveDraft(new WorkflowFormTemplateDraftCommand("QUALIFICATION_BORROW", "资质借阅申请",
                FormBusinessType.QUALIFICATION_BORROW, true, schema()));
        store.saveBinding(new WorkflowFormOaBindingCommand("QUALIFICATION_BORROW", "WEAVER", "WF_QUALIFICATION_BORROW",
                Map.of("workflowCode", "WF_QUALIFICATION_BORROW",
                        "mainFields", java.util.List.of(
                                Map.of("source", "formData.qualificationId", "target", "field_qualification"))),
                true));
        store.publish("QUALIFICATION_BORROW", "admin");
        return store;
    }

    private static Map<String, Object> schema() {
        return Map.of("fields", java.util.List.of(
                Map.of("key", "qualificationId", "label", "资质", "type", "qualification", "required", true),
                Map.of("key", "borrower", "label", "借用人", "type", "text", "required", true),
                Map.of("key", "department", "label", "部门", "type", "text", "required", true),
                Map.of("key", "projectId", "label", "项目", "type", "project", "required", true),
                Map.of("key", "purpose", "label", "用途", "type", "textarea", "required", true),
                Map.of("key", "expectedReturnDate", "label", "预计归还日期", "type", "date", "required", true),
                Map.of("key", "supportingFiles", "label", "附件", "type", "attachment", "required", true)
        ));
    }

    private static Map<String, Object> values() {
        Map<String, Object> values = valuesWithoutAttachment();
        values.put("supportingFiles", java.util.List.of(Map.of(
                "fileName", "授权书.pdf",
                "storagePath", "doc-insight://workflow-form-attachments/10/a.pdf"
        )));
        return values;
    }

    private static Map<String, Object> valuesWithoutAttachment() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("qualificationId", "1001");
        values.put("borrower", "小王");
        values.put("department", "投标管理部");
        values.put("projectId", "10");
        values.put("purpose", "用于投标文件编制");
        values.put("expectedReturnDate", "2026-05-10");
        return values;
    }

    static class CapturingOaWorkflowGateway implements OaWorkflowGateway {
        OaStartCommand lastCommand;
        OaStartResult result = new OaStartResult(true, "OA-1", null);

        @Override
        public OaStartResult startProcess(OaStartCommand command) {
            lastCommand = command;
            return result;
        }
    }
}
