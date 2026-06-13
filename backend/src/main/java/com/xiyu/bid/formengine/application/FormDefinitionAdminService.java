package com.xiyu.bid.formengine.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionAdminService {

    private final FormDefinitionRegistryRepository definitionRepository;
    private final ObjectMapper objectMapper;
    private final AdaptiveFormService adaptiveFormService;
    private final FormRuleManager formRuleManager;

    @Transactional(readOnly = true)
    public Page<FormDefinitionRegistryEntity> findAll(Pageable pageable) {
        return definitionRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public FormDefinitionRegistryEntity findById(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new AdaptiveFormService.FormDefinitionNotFoundException("id=" + id));
    }

    @Transactional
    public FormDefinitionRegistryEntity create(CreateFormDefinitionRequest request, String createdBy) {
        if (definitionRepository.existsByScope(request.scope())) {
            throw new IllegalArgumentException("Scope already exists: " + request.scope());
        }
        FormDefinitionRegistryEntity entity = new FormDefinitionRegistryEntity();
        entity.setScope(request.scope());
        entity.setScopeLabel(request.scopeLabel());
        entity.setVersion(1);
        entity.setSchemaJson(toJson(request.schema()));
        entity.setEnabled(true);
        entity.setOrgId(request.orgId());
        entity.setCreatedBy(createdBy);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(LocalDateTime.now());
        return definitionRepository.save(entity);
    }

    @Transactional
    public FormDefinitionRegistryEntity update(Long id, UpdateFormDefinitionRequest request) {
        FormDefinitionRegistryEntity entity = findById(id);
        entity.setScopeLabel(request.scopeLabel());
        entity.setSchemaJson(toJson(request.schema()));
        entity.setUpdatedAt(LocalDateTime.now());
        return definitionRepository.save(entity);
    }

    @Transactional
    public void delete(Long id) {
        FormDefinitionRegistryEntity entity = findById(id);
        entity.setEnabled(false);
        entity.setUpdatedAt(LocalDateTime.now());
        definitionRepository.save(entity);
        adaptiveFormService.invalidateCache(entity.getScope(), entity.getOrgId());
    }

    @Transactional
    public FormDefinitionRegistryEntity publish(Long id) {
        FormDefinitionRegistryEntity entity = findById(id);
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        FormDefinitionRegistryEntity saved = definitionRepository.save(entity);
        adaptiveFormService.invalidateCache(entity.getScope(), entity.getOrgId());
        log.info("Published form definition: id={}, scope={}, newVersion={}", id, entity.getScope(), entity.getVersion());
        return saved;
    }

    // 规则管理委托给 FormRuleManager
    public void saveVisibilityRules(Long definitionId, java.util.List<VisibilityRuleDto> rules) { formRuleManager.saveVisibilityRules(findById(definitionId), definitionId, rules); }
    public void saveConditionRules(Long definitionId, java.util.List<ConditionRuleDto> rules) { formRuleManager.saveConditionRules(findById(definitionId), definitionId, rules); }
    public void saveCrossFieldRules(Long definitionId, java.util.List<CrossFieldRuleDto> rules) { formRuleManager.saveCrossFieldRules(findById(definitionId), definitionId, rules); }
    public void saveTenantOverrides(Long definitionId, java.util.List<TenantOverrideDto> overrides) { formRuleManager.saveTenantOverrides(findById(definitionId), definitionId, overrides); }
    public java.util.List<com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity> getVisibilityRules(Long definitionId) { return formRuleManager.getVisibilityRules(definitionId); }
    public java.util.List<com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldConditionEntity> getConditionRules(Long definitionId) { return formRuleManager.getConditionRules(definitionId); }
    public java.util.List<com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity> getCrossFieldRules(Long definitionId) { return formRuleManager.getCrossFieldRules(definitionId); }
    public java.util.List<com.xiyu.bid.formengine.infrastructure.persistence.entity.TenantFormFieldOverrideEntity> getTenantOverrides(Long definitionId) { FormDefinitionRegistryEntity d = findById(definitionId); return formRuleManager.getTenantOverrides(d, definitionId); }

    private String toJson(Map<String, Object> schema) {
        try { return objectMapper.writeValueAsString(schema); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Invalid schema JSON: " + e.getMessage()); }
    }

    public record CreateFormDefinitionRequest(String scope, String scopeLabel, Long orgId, Map<String, Object> schema) {}
    public record UpdateFormDefinitionRequest(String scopeLabel, Map<String, Object> schema) {}
    public record VisibilityRuleDto(String fieldKey, String rolePattern, Long orgId, boolean visible, boolean readonly, boolean hidden) {}
    public record ConditionRuleDto(String sourceField, String operator, String targetValue, String action, String targetField, int displayOrder) {}
    public record CrossFieldRuleDto(String fieldA, String operator, String fieldB, String targetValue, String errorMessage, int priority) {}
    public record TenantOverrideDto(String fieldKey, String overrideType, String overrideValue) {}
}
