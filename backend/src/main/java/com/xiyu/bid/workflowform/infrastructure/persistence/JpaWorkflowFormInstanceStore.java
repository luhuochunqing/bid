package com.xiyu.bid.workflowform.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormInstanceStore;
import com.xiyu.bid.workflowform.domain.FormBusinessType;
import com.xiyu.bid.workflowform.domain.FormInstanceStatusPolicy;
import com.xiyu.bid.workflowform.domain.WorkflowFormStatus;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.OaProcessEventEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormInstanceEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.OaProcessEventJpaRepository;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.WorkflowFormInstanceJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaWorkflowFormInstanceStore implements WorkflowFormInstanceStore {

    private final WorkflowFormInstanceJpaRepository repository;
    private final OaProcessEventJpaRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public Long create(FormBusinessType businessType, String templateCode, Integer templateVersion, Long projectId,
                       String applicantName, Map<String, Object> formData, Map<String, Object> schemaSnapshot,
                       Map<String, Object> oaBindingSnapshot, Map<String, Object> oaPayload) {
        WorkflowFormInstanceEntity entity = new WorkflowFormInstanceEntity();
        entity.setBusinessType(businessType);
        entity.setTemplateCode(templateCode);
        entity.setTemplateVersion(templateVersion);
        entity.setProjectId(projectId);
        entity.setApplicantName(applicantName);
        entity.setStatus(WorkflowFormStatus.SUBMITTED);
        entity.setFormDataJson(writeJson(formData));
        entity.setSchemaSnapshotJson(writeJson(schemaSnapshot));
        entity.setOaBindingSnapshotJson(writeJson(oaBindingSnapshot));
        entity.setOaPayloadJson(writeJson(oaPayload));
        entity.setBusinessApplied(false);
        return repository.save(entity).getId();
    }

    @Override
    public void updateOaPayload(Long id, Map<String, Object> oaPayload) {
        WorkflowFormInstanceEntity entity = get(id);
        entity.setOaPayloadJson(writeJson(oaPayload));
        repository.save(entity);
    }

    @Override
    public void markOaApproving(Long id, String oaInstanceId) {
        WorkflowFormInstanceEntity entity = get(id);
        ensureTransition(entity.getStatus(), WorkflowFormStatus.OA_APPROVING);
        if (repository.markOaApprovingIfSubmitted(id, oaInstanceId) != 1) {
            throw new IllegalStateException("流程表单状态已变化，无法进入 OA 审批中: " + id);
        }
    }

    @Override
    public void markOaFailed(Long id, String oaInstanceId, String reason) {
        WorkflowFormInstanceEntity entity = get(id);
        ensureTransition(entity.getStatus(), WorkflowFormStatus.OA_FAILED);
        repository.markOaFailedIfNotTerminal(id, oaInstanceId, reason);
    }

    @Override
    public Optional<WorkflowFormInstanceRecord> findById(Long id) {
        return repository.findById(id).map(this::toRecord);
    }

    @Override
    public Optional<WorkflowFormInstanceRecord> findByOaInstanceId(String oaInstanceId) {
        return repository.findByOaInstanceId(oaInstanceId).map(this::toRecord);
    }

    @Override
    public boolean markOaApproved(Long id, String operatorName, String comment) {
        WorkflowFormInstanceEntity entity = get(id);
        if (entity.getStatus() != WorkflowFormStatus.OA_APPROVING) {
            return false;
        }
        ensureTransition(entity.getStatus(), WorkflowFormStatus.OA_APPROVED);
        return repository.markOaApprovedIfApproving(id, operatorName, comment) == 1;
    }

    @Override
    public void markOaRejected(Long id, String operatorName, String comment) {
        WorkflowFormInstanceEntity entity = get(id);
        if (entity.getStatus() != WorkflowFormStatus.OA_APPROVING) {
            return;
        }
        ensureTransition(entity.getStatus(), WorkflowFormStatus.OA_REJECTED);
        repository.markOaRejectedIfApproving(id, operatorName, comment);
    }

    @Override
    public void markBusinessApplied(Long id) {
        WorkflowFormInstanceEntity entity = get(id);
        ensureTransition(entity.getStatus(), WorkflowFormStatus.BUSINESS_APPLIED);
        if (repository.markBusinessAppliedIfOaApproved(id) != 1) {
            throw new IllegalStateException("流程表单状态已变化，无法应用业务: " + id);
        }
    }

    @Override
    public void markBusinessApplyFailed(Long id, String reason) {
        WorkflowFormInstanceEntity entity = get(id);
        entity.setBusinessApplyError(reason);
        repository.save(entity);
    }

    @Override
    public boolean isEventProcessed(String eventId) {
        return eventRepository.existsByEventId(eventId);
    }

    @Override
    public void recordEvent(Long formInstanceId, String oaInstanceId, String eventId, String eventType, String rawPayload) {
        OaProcessEventEntity event = new OaProcessEventEntity();
        event.setFormInstanceId(formInstanceId);
        event.setOaInstanceId(oaInstanceId);
        event.setEventId(eventId);
        event.setEventType(eventType);
        event.setRawPayload(rawPayload);
        eventRepository.save(event);
    }

    private WorkflowFormInstanceEntity get(Long id) {
        return repository.findById(id).orElseThrow(() -> new IllegalArgumentException("未找到流程表单: " + id));
    }

    private void ensureTransition(WorkflowFormStatus from, WorkflowFormStatus to) {
        if (!FormInstanceStatusPolicy.canTransit(from, to)) {
            throw new IllegalStateException("非法流程表单状态流转: " + from + " -> " + to);
        }
    }

    private WorkflowFormInstanceRecord toRecord(WorkflowFormInstanceEntity entity) {
        return new WorkflowFormInstanceRecord(
                entity.getId(),
                entity.getBusinessType(),
                entity.getTemplateCode(),
                entity.getTemplateVersion(),
                entity.getProjectId(),
                entity.getApplicantName(),
                entity.getStatus(),
                readJson(entity.getFormDataJson()),
                readOptionalJson(entity.getSchemaSnapshotJson()),
                readOptionalJson(entity.getOaBindingSnapshotJson()),
                readOptionalJson(entity.getOaPayloadJson()),
                entity.getOaInstanceId(),
                entity.isBusinessApplied(),
                entity.getBusinessApplyError()
        );
    }

    private String writeJson(Map<String, Object> formData) {
        try {
            return objectMapper.writeValueAsString(formData);
        } catch (IOException exception) {
            throw new IllegalArgumentException("表单数据序列化失败", exception);
        }
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("表单数据解析失败", exception);
        }
    }

    private Map<String, Object> readOptionalJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        return readJson(json);
    }
}
