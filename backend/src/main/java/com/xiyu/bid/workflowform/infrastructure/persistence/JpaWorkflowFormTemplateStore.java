package com.xiyu.bid.workflowform.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateStore;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.WorkflowFormTemplateJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaWorkflowFormTemplateStore implements WorkflowFormTemplateStore {

    private final WorkflowFormTemplateJpaRepository repository;
    private final ObjectMapper objectMapper;

    @Override
    public Optional<WorkflowFormTemplateRecord> findActiveByCode(String templateCode) {
        return repository.findById(templateCode)
                .filter(WorkflowFormTemplateEntity::isEnabled)
                .map(this::toRecord);
    }

    private WorkflowFormTemplateRecord toRecord(WorkflowFormTemplateEntity entity) {
        return new WorkflowFormTemplateRecord(
                entity.getTemplateCode(),
                entity.getBusinessType(),
                entity.getVersion(),
                readSchema(entity.getSchemaJson())
        );
    }

    private Map<String, Object> readSchema(String schemaJson) {
        try {
            return objectMapper.readValue(schemaJson, new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("流程表单模板解析失败", exception);
        }
    }
}
