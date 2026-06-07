package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.command.WorkflowFormSubmitCommand;
import com.xiyu.bid.workflowform.application.port.OaProcessBindingRecord;
import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.application.port.OaStartResult;
import com.xiyu.bid.workflowform.application.port.OaWorkflowGateway;
import com.xiyu.bid.workflowform.application.port.WorkflowFormAdminStore;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceStore;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateRecord;
import com.xiyu.bid.workflowform.application.view.WorkflowFormInstanceView;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.FormSubmissionValidator;
import com.xiyu.bid.workflowform.domain.ValidationResult;
import com.xiyu.bid.workflowform.domain.WorkflowFormOaMappingPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormOaPayloadPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormSchemaPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowFormSubmissionService {

    private static final String WORKFLOW_FORM_ATTACHMENT_PREFIX = "doc-insight://workflow-form-attachments/";

    private final WorkflowFormInstanceStore store;
    private final WorkflowFormAdminStore adminStore;
    private final WorkflowFormSchemaAssembler schemaAssembler;
    private final OaWorkflowGateway oaWorkflowGateway;
    private final WorkflowFormAccessGuard accessGuard;
    private final TransactionTemplate transactionTemplate;

    public WorkflowFormInstanceView submit(WorkflowFormSubmitCommand command) {
        accessGuard.assertCanAccessProject(command.projectId());
        requireConsistentProjectId(command);
        WorkflowFormTemplateRecord template = adminStore.findActive(command.templateCode())
                .orElseThrow(() -> new IllegalArgumentException("流程表单未发布"));
        OaProcessBindingRecord binding = adminStore.findBinding(command.templateCode())
                .filter(OaProcessBindingRecord::enabled)
                .orElseThrow(() -> new IllegalArgumentException("流程表单未配置启用的 OA 绑定"));
        requireValidTemplateAndBinding(template, binding);
        validate(command, template);
        requireAttachmentReferencesOwnedByProject(command);
        Long id = transactionTemplate.execute(status ->
                store.create(template.businessType(), command.templateCode(), template.version(), command.projectId(),
                        command.applicantName(), command.formData(), template.schema(), binding.fieldMapping(), Map.of()));
        Map<String, Object> oaPayload = WorkflowFormOaPayloadPolicy.buildPayload(
                binding.fieldMapping(), command.formData(),
                Map.of("formInstanceId", String.valueOf(id), "templateCode", command.templateCode()),
                Map.of("name", command.applicantName() == null ? "" : command.applicantName()), false);
        transactionTemplate.executeWithoutResult(status -> store.updateOaPayload(id, oaPayload));
        OaStartResult result = oaWorkflowGateway.startProcess(new OaStartCommand(
                binding.workflowCode(), template.businessType(), id, command.applicantName(), command.formData(),
                command.templateCode(), oaPayload, false));
        if (!result.success()) {
            transactionTemplate.executeWithoutResult(status ->
                    store.markOaFailed(id, result.oaInstanceId(),
                            result.errorMessage() == null ? "OA 流程发起失败" : result.errorMessage()));
            return toView(store.findById(id).orElseThrow());
        }
        transactionTemplate.executeWithoutResult(status -> store.markOaApproving(id, result.oaInstanceId()));
        return toView(store.findById(id).orElseThrow());
    }

    private void requireConsistentProjectId(WorkflowFormSubmitCommand command) {
        Object formProjectId = command.formData().get("projectId");
        if (command.projectId() != null && formProjectId != null
                && !String.valueOf(command.projectId()).equals(String.valueOf(formProjectId))) {
            throw new IllegalArgumentException("表单项目必须与提交项目一致");
        }
    }

    private void validate(WorkflowFormSubmitCommand command, WorkflowFormTemplateRecord template) {
        ValidationResult schemaDataResult = FormSubmissionValidator.validate(
                schemaAssembler.toSchema(template), command.formData());
        if (!schemaDataResult.valid()) {
            throw new IllegalArgumentException(String.join(";", schemaDataResult.errors()));
        }
        if (command.businessType() == FormBusinessType.QUALIFICATION_BORROW) {
            ValidationResult result = FormSubmissionValidator.validateQualificationBorrow(command.formData());
            if (!result.valid()) {
                throw new IllegalArgumentException(String.join(";", result.errors()));
            }
        }
    }

    private void requireAttachmentReferencesOwnedByProject(WorkflowFormSubmitCommand command) {
        String expectedPrefix = command.projectId() == null
                ? null
                : WORKFLOW_FORM_ATTACHMENT_PREFIX + command.projectId() + "/";
        command.formData().values().forEach(value -> requireAttachmentReference(value, expectedPrefix));
    }

    private void requireAttachmentReference(Object value, String expectedPrefix) {
        if (value instanceof List<?> list) {
            list.forEach(item -> requireAttachmentReference(item, expectedPrefix));
            return;
        }
        if (!(value instanceof Map<?, ?> map) || !map.containsKey("fileName")) {
            return;
        }
        String fileUrl = stringValue(map.get("fileUrl"));
        String storagePath = stringValue(map.get("storagePath"));
        String reference = fileUrl == null ? storagePath : fileUrl;
        if (reference == null) {
            return;
        }
        if (expectedPrefix == null || !reference.startsWith(expectedPrefix)) {
            throw new IllegalArgumentException("附件必须通过当前项目的流程表单上传入口上传");
        }
        if (storagePath != null && !storagePath.startsWith(WORKFLOW_FORM_ATTACHMENT_PREFIX)) {
            throw new IllegalArgumentException("附件存储引用格式不正确");
        }
    }

    private String stringValue(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        return String.valueOf(value).trim();
    }

    private void requireValidTemplateAndBinding(WorkflowFormTemplateRecord template, OaProcessBindingRecord binding) {
        ValidationResult schemaResult = WorkflowFormSchemaPolicy.validate(template.schema());
        ValidationResult mappingResult = WorkflowFormOaMappingPolicy.validate(binding.fieldMapping());
        if (!schemaResult.valid() || !mappingResult.valid()) {
            throw new IllegalArgumentException("流程表单模板或 OA 绑定配置无效");
        }
    }

    private WorkflowFormInstanceView toView(WorkflowFormInstanceRecord record) {
        return new WorkflowFormInstanceView(record.id(), record.businessType(), record.templateCode(),
                record.projectId(), record.status(), record.oaInstanceId(), record.businessApplyError());
    }
}
