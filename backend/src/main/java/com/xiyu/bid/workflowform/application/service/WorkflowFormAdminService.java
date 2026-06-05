package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.WorkflowFormConfigException;
import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;
import com.xiyu.bid.workflowform.application.port.OaStartCommand;
import com.xiyu.bid.workflowform.application.port.OaStartResult;
import com.xiyu.bid.workflowform.application.port.OaWorkflowGateway;
import com.xiyu.bid.workflowform.application.port.OaProcessBindingRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormAdminStore;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateAdminRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateVersionRecord;
import com.xiyu.bid.workflowform.application.view.WorkflowFormTrialSubmitView;
import com.xiyu.bid.workflowform.domain.ValidationResult;
import com.xiyu.bid.workflowform.domain.WorkflowFormOaMappingPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormOaPayloadPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormSchemaPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class WorkflowFormAdminService {

    private final WorkflowFormAdminStore store;
    private final OaWorkflowGateway oaWorkflowGateway;

    public List<WorkflowFormTemplateAdminRecord> listTemplates() {
        return store.listTemplates();
    }

    public WorkflowFormTemplateAdminRecord saveDraft(WorkflowFormTemplateDraftCommand command) {
        requireValidSchema(command.schema());
        return store.saveDraft(command);
    }

    public OaProcessBindingRecord saveOaBinding(WorkflowFormOaBindingCommand command) {
        requireValidMapping(command.fieldMapping());
        return store.saveBinding(command);
    }

    public WorkflowFormTemplateAdminRecord publish(String templateCode, String publishedBy) {
        WorkflowFormTemplateAdminRecord draft = store.findDraft(templateCode)
                .orElseThrow(() -> new WorkflowFormConfigException("流程表单草稿不存在"));
        requireValidSchema(draft.schema());
        OaProcessBindingRecord binding = store.findBinding(templateCode)
                .filter(OaProcessBindingRecord::enabled)
                .orElseThrow(() -> new WorkflowFormConfigException("流程表单未配置启用的 OA 绑定"));
        requireValidMapping(binding.fieldMapping());
        return store.publish(templateCode, publishedBy);
    }

    public List<WorkflowFormTemplateVersionRecord> listVersions(String templateCode) {
        return store.listVersions(templateCode);
    }

    public WorkflowFormTemplateAdminRecord rollback(String templateCode, int version, String operator) {
        store.findDraft(templateCode).orElseThrow(() -> new WorkflowFormConfigException("流程表单草稿不存在"));
        try {
            return store.rollback(templateCode, version, operator);
        } catch (IllegalArgumentException exception) {
            throw new WorkflowFormConfigException(exception.getMessage());
        }
    }

    public WorkflowFormTrialSubmitView previewTrialSubmit(
            String templateCode,
            Map<String, Object> formData,
            String applicantName
    ) {
        OaProcessBindingRecord binding = store.findBinding(templateCode)
                .orElseThrow(() -> new WorkflowFormConfigException("流程表单未配置 OA 绑定"));
        requireValidMapping(binding.fieldMapping());
        Map<String, Object> payload = WorkflowFormOaPayloadPolicy.buildPayload(
                binding.fieldMapping(),
                formData,
                Map.of("formInstanceId", "PREVIEW", "templateCode", templateCode),
                Map.of("name", applicantName == null ? "" : applicantName),
                true
        );
        OaStartResult result = oaWorkflowGateway.startProcess(new OaStartCommand(
                binding.workflowCode(), null, null, applicantName, formData, templateCode, payload, true));
        return new WorkflowFormTrialSubmitView(result.success(), result.oaInstanceId(), result.errorMessage(), payload);
    }

    private void requireValidSchema(Map<String, Object> schema) {
        requireValid(WorkflowFormSchemaPolicy.validate(schema));
    }

    private void requireValidMapping(Map<String, Object> mapping) {
        Map<String, Object> merged = new LinkedHashMap<>(mapping == null ? Map.of() : mapping);
        requireValid(WorkflowFormOaMappingPolicy.validate(merged));
    }

    private void requireValid(ValidationResult result) {
        if (!result.valid()) {
            throw new WorkflowFormConfigException(String.join(";", result.errors()));
        }
    }
}
