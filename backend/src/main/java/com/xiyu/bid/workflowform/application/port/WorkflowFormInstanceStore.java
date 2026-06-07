package com.xiyu.bid.workflowform.application.port;

import com.xiyu.bid.workflowform.domain.FormBusinessType;

import java.util.Map;
import java.util.Optional;

public interface WorkflowFormInstanceStore {
    Long create(FormBusinessType businessType, String templateCode, Integer templateVersion, Long projectId,
                String applicantName, Map<String, Object> formData, Map<String, Object> schemaSnapshot,
                Map<String, Object> oaBindingSnapshot, Map<String, Object> oaPayload);

    void updateOaPayload(Long id, Map<String, Object> oaPayload);

    void markOaApproving(Long id, String oaInstanceId);

    void markOaFailed(Long id, String oaInstanceId, String reason);

    Optional<WorkflowFormInstanceRecord> findById(Long id);

    Optional<WorkflowFormInstanceRecord> findByOaInstanceId(String oaInstanceId);

    boolean markOaApproved(Long id, String operatorName, String comment);

    void markOaRejected(Long id, String operatorName, String comment);

    void markBusinessApplied(Long id);

    void markBusinessApplyFailed(Long id, String reason);

    boolean isEventProcessed(String eventId);

    void recordEvent(Long formInstanceId, String oaInstanceId, String eventId, String eventType, String rawPayload);
}
