package com.xiyu.bid.formengine.application;

import com.xiyu.bid.formengine.infrastructure.persistence.CrossFieldValidationRuleRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldConditionRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldVisibilityRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.TenantFormFieldOverrideRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldConditionEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.TenantFormFieldOverrideEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FormRuleManager {

    private final FormFieldVisibilityRepository visibilityRepository;
    private final FormFieldConditionRepository conditionRepository;
    private final CrossFieldValidationRuleRepository crossFieldRuleRepository;
    private final TenantFormFieldOverrideRepository tenantOverrideRepository;
    private final AdaptiveFormService adaptiveFormService;

    @Transactional
    public void saveVisibilityRules(FormDefinitionRegistryEntity definition, Long definitionId, List<FormDefinitionAdminService.VisibilityRuleDto> rules) {
        List<FormFieldVisibilityEntity> old = visibilityRepository.findByDefinitionId(definitionId);
        visibilityRepository.deleteAll(old);
        LocalDateTime now = LocalDateTime.now();
        List<FormFieldVisibilityEntity> entities = rules.stream().map(rule -> {
            FormFieldVisibilityEntity e = new FormFieldVisibilityEntity();
            e.setDefinition(definition); e.setFieldKey(rule.fieldKey()); e.setRolePattern(rule.rolePattern());
            e.setOrgId(rule.orgId()); e.setVisible(rule.visible()); e.setReadonly(rule.readonly());
            e.setHidden(rule.hidden()); e.setCreatedAt(now);
            return e;
        }).toList();
        visibilityRepository.saveAll(entities);
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    @Transactional
    public void saveConditionRules(FormDefinitionRegistryEntity definition, Long definitionId, List<FormDefinitionAdminService.ConditionRuleDto> rules) {
        List<FormFieldConditionEntity> old = conditionRepository.findByDefinitionId(definitionId);
        conditionRepository.deleteAll(old);
        LocalDateTime now = LocalDateTime.now();
        List<FormFieldConditionEntity> entities = rules.stream().map(rule -> {
            FormFieldConditionEntity e = new FormFieldConditionEntity();
            e.setDefinition(definition); e.setSourceField(rule.sourceField()); e.setOperator(rule.operator());
            e.setTargetValue(rule.targetValue()); e.setAction(rule.action()); e.setTargetField(rule.targetField());
            e.setDisplayOrder(rule.displayOrder()); e.setCreatedAt(now);
            return e;
        }).toList();
        conditionRepository.saveAll(entities);
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    @Transactional(readOnly = true)
    public List<FormFieldVisibilityEntity> getVisibilityRules(Long definitionId) {
        return visibilityRepository.findByDefinitionId(definitionId);
    }

    @Transactional(readOnly = true)
    public List<FormFieldConditionEntity> getConditionRules(Long definitionId) {
        return conditionRepository.findByDefinitionId(definitionId);
    }

    @Transactional
    public void saveCrossFieldRules(FormDefinitionRegistryEntity definition, Long definitionId, List<FormDefinitionAdminService.CrossFieldRuleDto> rules) {
        List<CrossFieldValidationRuleEntity> old = crossFieldRuleRepository.findByDefinitionIdOrderByPriorityAsc(definitionId);
        crossFieldRuleRepository.deleteAll(old);
        LocalDateTime now = LocalDateTime.now();
        List<CrossFieldValidationRuleEntity> entities = rules.stream().map(r -> {
            CrossFieldValidationRuleEntity e = new CrossFieldValidationRuleEntity();
            e.setDefinition(definition); e.setScope(definition.getScope()); e.setSourceField(r.fieldA());
            e.setOperator(r.operator()); e.setTargetField(r.fieldB()); e.setTargetValue(r.targetValue());
            e.setErrorMessage(r.errorMessage()); e.setPriority(r.priority()); e.setCreatedAt(now);
            return e;
        }).toList();
        crossFieldRuleRepository.saveAll(entities);
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    @Transactional(readOnly = true)
    public List<CrossFieldValidationRuleEntity> getCrossFieldRules(Long definitionId) {
        return crossFieldRuleRepository.findByDefinitionIdOrderByPriorityAsc(definitionId);
    }

    @Transactional
    public void saveTenantOverrides(FormDefinitionRegistryEntity definition, Long definitionId, List<FormDefinitionAdminService.TenantOverrideDto> overrides) {
        Long orgId = definition.getOrgId() != null ? definition.getOrgId() : 0L;
        List<TenantFormFieldOverrideEntity> old = tenantOverrideRepository.findByDefinitionIdAndOrgId(definitionId, orgId);
        tenantOverrideRepository.deleteAll(old);
        LocalDateTime now = LocalDateTime.now();
        List<TenantFormFieldOverrideEntity> entities = overrides.stream()
                .filter(o -> o.fieldKey() != null && !o.fieldKey().isBlank())
                .map(o -> {
                    TenantFormFieldOverrideEntity e = new TenantFormFieldOverrideEntity();
                    e.setDefinition(definition); e.setOrgId(orgId); e.setFieldKey(o.fieldKey());
                    e.setOverrideType(o.overrideType()); e.setOverrideValue(o.overrideValue()); e.setCreatedAt(now);
                    return e;
                }).toList();
        tenantOverrideRepository.saveAll(entities);
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    @Transactional(readOnly = true)
    public List<TenantFormFieldOverrideEntity> getTenantOverrides(FormDefinitionRegistryEntity definition, Long definitionId) {
        Long orgId = definition.getOrgId() != null ? definition.getOrgId() : 0L;
        return tenantOverrideRepository.findByDefinitionIdAndOrgId(definitionId, orgId);
    }
}
