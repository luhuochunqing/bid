package com.xiyu.bid.workflowform.application.service;

import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceStore;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.FormInstanceStatusPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.HashSet;
import java.util.Set;

class InMemoryWorkflowFormInstanceStore implements WorkflowFormInstanceStore {
    private final Map<Long, WorkflowFormInstanceRecord> records = new LinkedHashMap<>();
    private long nextId = 1L;
    private final Set<String> eventIds = new HashSet<>();

    public Long create(FormBusinessType businessType, String templateCode, Long projectId,
                       String applicantName, Map<String, Object> formData) {
        return create(businessType, templateCode, 1, projectId, applicantName, formData,
                Map.of(), Map.of(), Map.of());
    }

    @Override
    public Long create(FormBusinessType businessType, String templateCode, Integer templateVersion, Long projectId,
                       String applicantName, Map<String, Object> formData, Map<String, Object> schemaSnapshot,
                       Map<String, Object> oaBindingSnapshot, Map<String, Object> oaPayload) {
        Long id = nextId++;
        records.put(id, new WorkflowFormInstanceRecord(id, businessType, templateCode, templateVersion, projectId, applicantName,
                WorkflowFormStatus.SUBMITTED, new LinkedHashMap<>(formData), new LinkedHashMap<>(schemaSnapshot),
                new LinkedHashMap<>(oaBindingSnapshot), new LinkedHashMap<>(oaPayload), null, false, null));
        return id;
    }

    @Override
    public void updateOaPayload(Long id, Map<String, Object> oaPayload) {
        WorkflowFormInstanceRecord record = records.get(id);
        records.put(id, record.withOaPayload(new LinkedHashMap<>(oaPayload)));
    }

    @Override
    public void markOaApproving(Long id, String oaInstanceId) {
        WorkflowFormInstanceRecord record = records.get(id);
        ensureTransition(record.status(), WorkflowFormStatus.OA_APPROVING);
        if (record.status() != WorkflowFormStatus.SUBMITTED) {
            throw new IllegalStateException("流程表单状态已变化，无法进入 OA 审批中: " + id);
        }
        records.put(id, record.withStatus(WorkflowFormStatus.OA_APPROVING).withOaInstanceId(oaInstanceId));
    }

    @Override
    public void markOaFailed(Long id, String oaInstanceId, String reason) {
        WorkflowFormInstanceRecord record = records.get(id);
        ensureTransition(record.status(), WorkflowFormStatus.OA_FAILED);
        records.put(id, record.withStatus(WorkflowFormStatus.OA_FAILED)
                .withOaInstanceId(oaInstanceId)
                .withBusinessApplyError(reason));
    }

    @Override
    public Optional<WorkflowFormInstanceRecord> findById(Long id) {
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public Optional<WorkflowFormInstanceRecord> findByOaInstanceId(String oaInstanceId) {
        return records.values().stream().filter(record -> oaInstanceId.equals(record.oaInstanceId())).findFirst();
    }

    @Override
    public boolean markOaApproved(Long id, String operatorName, String comment) {
        WorkflowFormInstanceRecord record = records.get(id);
        if (record.status() != WorkflowFormStatus.OA_APPROVING) {
            return false;
        }
        ensureTransition(record.status(), WorkflowFormStatus.OA_APPROVED);
        records.put(id, record.withStatus(WorkflowFormStatus.OA_APPROVED));
        return true;
    }

    @Override
    public void markOaRejected(Long id, String operatorName, String comment) {
        WorkflowFormInstanceRecord record = records.get(id);
        if (record.status() != WorkflowFormStatus.OA_APPROVING) {
            return;
        }
        ensureTransition(record.status(), WorkflowFormStatus.OA_REJECTED);
        records.put(id, record.withStatus(WorkflowFormStatus.OA_REJECTED));
    }

    @Override
    public void markBusinessApplied(Long id) {
        WorkflowFormInstanceRecord record = records.get(id);
        ensureTransition(record.status(), WorkflowFormStatus.BUSINESS_APPLIED);
        records.put(id, record.withStatus(WorkflowFormStatus.BUSINESS_APPLIED).withBusinessApplied(true));
    }

    @Override
    public void markBusinessApplyFailed(Long id, String reason) {
        WorkflowFormInstanceRecord record = records.get(id);
        records.put(id, record.withBusinessApplyError(reason));
    }
    @Override
    public boolean isEventProcessed(String eventId) {
        return eventIds.contains(eventId);
    }

    @Override
    public void recordEvent(Long formInstanceId, String oaInstanceId, String eventId, String eventType, String rawPayload) {
        eventIds.add(eventId);
    }

    private void ensureTransition(WorkflowFormStatus from, WorkflowFormStatus to) {
        if (!FormInstanceStatusPolicy.canTransit(from, to)) {
            throw new IllegalStateException("非法流程表单状态流转: " + from + " -> " + to);
        }
    }
}
