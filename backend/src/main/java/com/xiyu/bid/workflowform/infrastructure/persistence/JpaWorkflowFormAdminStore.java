package com.xiyu.bid.workflowform.infrastructure.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.workflowform.application.command.WorkflowFormOaBindingCommand;
import com.xiyu.bid.workflowform.application.command.WorkflowFormTemplateDraftCommand;
import com.xiyu.bid.workflowform.application.port.OaProcessBindingRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormAdminStore;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateAdminRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateRecord;
import com.xiyu.bid.workflowform.application.port.WorkflowFormTemplateVersionRecord;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.OaProcessBindingEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateDraftEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.entity.WorkflowFormTemplateVersionEntity;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.OaProcessBindingJpaRepository;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.WorkflowFormTemplateDraftJpaRepository;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.WorkflowFormTemplateJpaRepository;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.WorkflowFormTemplateVersionJpaRepository;
import com.xiyu.bid.workflowform.infrastructure.persistence.repository.WorkflowFormTemplateVersionMaxRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JpaWorkflowFormAdminStore implements WorkflowFormAdminStore {

    private final WorkflowFormTemplateDraftJpaRepository draftRepository;
    private final WorkflowFormTemplateVersionJpaRepository versionRepository;
    private final WorkflowFormTemplateJpaRepository activeRepository;
    private final OaProcessBindingJpaRepository bindingRepository;
    private final ObjectMapper objectMapper;

    @Override
    public List<WorkflowFormTemplateAdminRecord> listTemplates() {
        List<WorkflowFormTemplateDraftEntity> drafts = draftRepository.findAll();
        List<String> templateCodes = drafts.stream().map(WorkflowFormTemplateDraftEntity::getTemplateCode).toList();
        if (templateCodes.isEmpty()) {
            return List.of();
        }
        Map<String, Integer> versions = versionRepository.findMaxVersions(templateCodes).stream()
                .collect(java.util.stream.Collectors.toMap(
                        WorkflowFormTemplateVersionMaxRow::getTemplateCode,
                        WorkflowFormTemplateVersionMaxRow::getVersion));
        Map<String, OaProcessBindingRecord> bindings = bindingRepository.findAllById(templateCodes).stream()
                .map(this::toBindingRecord)
                .collect(java.util.stream.Collectors.toMap(OaProcessBindingRecord::templateCode, binding -> binding));
        return drafts.stream().map(entity -> toAdminRecord(
                entity,
                versions.getOrDefault(entity.getTemplateCode(), 0),
                bindings.get(entity.getTemplateCode())
        )).toList();
    }

    @Override
    public Optional<WorkflowFormTemplateAdminRecord> findDraft(String templateCode) {
        return draftRepository.findByTemplateCode(templateCode).map(this::toAdminRecord);
    }

    @Override
    public Optional<WorkflowFormTemplateRecord> findActive(String templateCode) {
        return activeRepository.findById(templateCode)
                .filter(WorkflowFormTemplateEntity::isEnabled)
                .map(entity -> new WorkflowFormTemplateRecord(entity.getTemplateCode(), entity.getBusinessType(),
                        entity.getVersion(), readJson(entity.getSchemaJson())));
    }

    @Override
    public Optional<OaProcessBindingRecord> findBinding(String templateCode) {
        return bindingRepository.findById(templateCode).map(this::toBindingRecord);
    }

    @Override
    public List<WorkflowFormTemplateVersionRecord> listVersions(String templateCode) {
        return versionRepository.findByTemplateCodeOrderByVersionDesc(templateCode).stream()
                .map(this::toVersionRecord).toList();
    }

    @Override
    @Transactional
    public WorkflowFormTemplateAdminRecord saveDraft(WorkflowFormTemplateDraftCommand command) {
        WorkflowFormTemplateDraftEntity entity = draftRepository.findByTemplateCode(command.templateCode())
                .orElseGet(WorkflowFormTemplateDraftEntity::new);
        entity.setTemplateCode(command.templateCode());
        entity.setName(command.name());
        entity.setBusinessType(command.businessType());
        entity.setEnabled(command.enabled());
        entity.setStatus("DRAFT");
        entity.setDraftSchemaJson(writeJson(command.schema()));
        return toAdminRecord(draftRepository.save(entity));
    }

    @Override
    @Transactional
    public OaProcessBindingRecord saveBinding(WorkflowFormOaBindingCommand command) {
        OaProcessBindingEntity entity = bindingRepository.findById(command.templateCode())
                .orElseGet(OaProcessBindingEntity::new);
        entity.setTemplateCode(command.templateCode());
        entity.setProvider(command.provider());
        entity.setWorkflowCode(command.workflowCode());
        entity.setFieldMappingJson(writeJson(command.fieldMapping()));
        entity.setEnabled(command.enabled());
        return toBindingRecord(bindingRepository.save(entity));
    }

    @Override
    @Transactional
    public WorkflowFormTemplateAdminRecord publish(String templateCode, String publishedBy) {
        WorkflowFormTemplateDraftEntity draft = draftRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new IllegalArgumentException("流程表单草稿不存在"));
        int nextVersion = versionRepository.findMaxVersion(templateCode) + 1;
        WorkflowFormTemplateVersionEntity version = new WorkflowFormTemplateVersionEntity();
        version.setTemplateCode(draft.getTemplateCode());
        version.setName(draft.getName());
        version.setBusinessType(draft.getBusinessType());
        version.setVersion(nextVersion);
        version.setSchemaJson(draft.getDraftSchemaJson());
        version.setEnabled(draft.isEnabled());
        version.setPublishedBy(publishedBy);
        version.setPublishedAt(LocalDateTime.now());
        versionRepository.save(version);
        upsertActiveProjection(draft, nextVersion);
        draft.setStatus("PUBLISHED");
        draftRepository.save(draft);
        return new WorkflowFormTemplateAdminRecord(templateCode, draft.getName(), draft.getBusinessType(),
                nextVersion, draft.isEnabled(), "PUBLISHED", readJson(draft.getDraftSchemaJson()),
                findBinding(templateCode).orElse(null));
    }

    @Override
    @Transactional
    public WorkflowFormTemplateAdminRecord rollback(String templateCode, int targetVersion, String operator) {
        WorkflowFormTemplateDraftEntity draft = draftRepository.findByTemplateCode(templateCode)
                .orElseThrow(() -> new IllegalArgumentException("流程表单草稿不存在"));
        WorkflowFormTemplateVersionEntity target = versionRepository.findByTemplateCodeAndVersion(templateCode, targetVersion)
                .orElseThrow(() -> new IllegalArgumentException("未找到目标历史版本: " + targetVersion));
        draft.setName(target.getName());
        draft.setBusinessType(target.getBusinessType());
        draft.setEnabled(target.isEnabled());
        draft.setDraftSchemaJson(target.getSchemaJson());
        draft.setStatus("DRAFT");
        draftRepository.save(draft);
        return publish(templateCode, operator);
    }

    private void upsertActiveProjection(WorkflowFormTemplateDraftEntity draft, int version) {
        WorkflowFormTemplateEntity active = activeRepository.findById(draft.getTemplateCode())
                .orElseGet(WorkflowFormTemplateEntity::new);
        active.setTemplateCode(draft.getTemplateCode());
        active.setName(draft.getName());
        active.setBusinessType(draft.getBusinessType());
        active.setVersion(version);
        active.setSchemaJson(draft.getDraftSchemaJson());
        active.setEnabled(draft.isEnabled());
        activeRepository.save(active);
    }

    private WorkflowFormTemplateAdminRecord toAdminRecord(WorkflowFormTemplateDraftEntity entity) {
        return toAdminRecord(entity, versionRepository.findMaxVersion(entity.getTemplateCode()),
                findBinding(entity.getTemplateCode()).orElse(null));
    }

    private WorkflowFormTemplateAdminRecord toAdminRecord(
            WorkflowFormTemplateDraftEntity entity,
            Integer version,
            OaProcessBindingRecord binding
    ) {
        return new WorkflowFormTemplateAdminRecord(entity.getTemplateCode(), entity.getName(), entity.getBusinessType(),
                version, entity.isEnabled(), entity.getStatus(), readJson(entity.getDraftSchemaJson()), binding);
    }

    private OaProcessBindingRecord toBindingRecord(OaProcessBindingEntity entity) {
        return new OaProcessBindingRecord(entity.getTemplateCode(), entity.getProvider(), entity.getWorkflowCode(),
                readJson(entity.getFieldMappingJson()), entity.isEnabled());
    }

    private WorkflowFormTemplateVersionRecord toVersionRecord(WorkflowFormTemplateVersionEntity entity) {
        return new WorkflowFormTemplateVersionRecord(
                entity.getTemplateCode(),
                entity.getVersion(),
                entity.getName(),
                entity.getBusinessType(),
                entity.isEnabled(),
                entity.getPublishedBy(),
                entity.getPublishedAt(),
                readJson(entity.getSchemaJson())
        );
    }

    private Map<String, Object> readJson(String json) {
        try {
            return objectMapper.readValue(json == null || json.isBlank() ? "{}" : json,
                    new TypeReference<LinkedHashMap<String, Object>>() { });
        } catch (IOException exception) {
            throw new IllegalStateException("流程表单配置解析失败", exception);
        }
    }

    private String writeJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (IOException exception) {
            throw new IllegalStateException("流程表单配置序列化失败", exception);
        }
    }
}
