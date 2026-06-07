package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;

import java.util.Map;

public record WorkflowFormInstanceRecord(
        Long id,
        FormBusinessType businessType,
        String templateCode,
        Integer templateVersion,
        Long projectId,
        String applicantName,
        WorkflowFormStatus status,
        Map<String, Object> formData,
        Map<String, Object> schemaSnapshot,
        Map<String, Object> oaBindingSnapshot,
        Map<String, Object> oaPayload,
        String oaInstanceId,
        boolean businessApplied,
        String businessApplyError
) {
    public WorkflowFormInstanceRecord withStatus(WorkflowFormStatus newStatus) {
        return new WorkflowFormInstanceRecord(id, businessType, templateCode, templateVersion, projectId, applicantName,
                newStatus, formData, schemaSnapshot, oaBindingSnapshot, oaPayload, oaInstanceId, businessApplied, businessApplyError);
    }

    public WorkflowFormInstanceRecord withOaInstanceId(String newOaInstanceId) {
        return new WorkflowFormInstanceRecord(id, businessType, templateCode, templateVersion, projectId, applicantName,
                status, formData, schemaSnapshot, oaBindingSnapshot, oaPayload, newOaInstanceId, businessApplied, businessApplyError);
    }

    public WorkflowFormInstanceRecord withOaPayload(Map<String, Object> newOaPayload) {
        return new WorkflowFormInstanceRecord(id, businessType, templateCode, templateVersion, projectId, applicantName,
                status, formData, schemaSnapshot, oaBindingSnapshot, newOaPayload, oaInstanceId, businessApplied, businessApplyError);
    }

    public WorkflowFormInstanceRecord withBusinessApplied(boolean applied) {
        return new WorkflowFormInstanceRecord(id, businessType, templateCode, templateVersion, projectId, applicantName,
                status, formData, schemaSnapshot, oaBindingSnapshot, oaPayload, oaInstanceId, applied, businessApplyError);
    }

    public WorkflowFormInstanceRecord withBusinessApplyError(String error) {
        return new WorkflowFormInstanceRecord(id, businessType, templateCode, templateVersion, projectId, applicantName,
                status, formData, schemaSnapshot, oaBindingSnapshot, oaPayload, oaInstanceId, businessApplied, error);
    }
}
