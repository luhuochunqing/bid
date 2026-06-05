// Input: 表单定义 DTO
// Output: CRUD 操作结果
// Pos: Application 层（管理端编排）
// 维护声明: 仅编排 CRUD 逻辑.
package com.xiyu.bid.formengine.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldConditionRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldVisibilityRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.CrossFieldValidationRuleRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.TenantFormFieldOverrideRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldConditionEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.TenantFormFieldOverrideEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表单定义管理服务。
 * 提供创建、更新、发布、可见性规则管理等功能。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionAdminService {

    private final FormDefinitionRegistryRepository definitionRepository;
    private final FormFieldVisibilityRepository visibilityRepository;
    private final FormFieldConditionRepository conditionRepository;
    private final CrossFieldValidationRuleRepository crossFieldRuleRepository;
    private final TenantFormFieldOverrideRepository tenantOverrideRepository;
    private final ObjectMapper objectMapper;
    private final AdaptiveFormService adaptiveFormService;

    /**
     * 分页查询所有表单定义
     */
    @Transactional(readOnly = true)
    public Page<FormDefinitionRegistryEntity> findAll(Pageable pageable) {
        return definitionRepository.findAll(pageable);
    }

    /**
     * 根据 ID 查询
     */
    @Transactional(readOnly = true)
    public FormDefinitionRegistryEntity findById(Long id) {
        return definitionRepository.findById(id)
                .orElseThrow(() -> new AdaptiveFormService.FormDefinitionNotFoundException("id=" + id));
    }

    /**
     * 创建新表单定义
     */
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

    /**
     * 更新表单定义（仅更新 schemaJson）
     */
    @Transactional
    public FormDefinitionRegistryEntity update(Long id, UpdateFormDefinitionRequest request) {
        FormDefinitionRegistryEntity entity = findById(id);
        entity.setScopeLabel(request.scopeLabel());
        entity.setSchemaJson(toJson(request.schema()));
        entity.setUpdatedAt(LocalDateTime.now());
        return definitionRepository.save(entity);
    }

    /**
     * 软删除（禁用）
     */
    @Transactional
    public void delete(Long id) {
        FormDefinitionRegistryEntity entity = findById(id);
        entity.setEnabled(false);
        entity.setUpdatedAt(LocalDateTime.now());
        definitionRepository.save(entity);

        // 清除缓存
        adaptiveFormService.invalidateCache(entity.getScope(), entity.getOrgId());
    }

    /**
     * 发布（递增版本号 + 清除缓存）
     */
    @Transactional
    public FormDefinitionRegistryEntity publish(Long id) {
        FormDefinitionRegistryEntity entity = findById(id);
        entity.setVersion(entity.getVersion() + 1);
        entity.setUpdatedAt(LocalDateTime.now());
        FormDefinitionRegistryEntity saved = definitionRepository.save(entity);

        // 清除缓存
        adaptiveFormService.invalidateCache(entity.getScope(), entity.getOrgId());

        log.info("Published form definition: id={}, scope={}, newVersion={}",
                id, entity.getScope(), entity.getVersion());
        return saved;
    }

    /**
     * 保存可见性规则
     */
    @Transactional
    public void saveVisibilityRules(Long definitionId, List<VisibilityRuleDto> rules) {
        FormDefinitionRegistryEntity definition = findById(definitionId);

        // 删除旧规则
        List<FormFieldVisibilityEntity> old = visibilityRepository.findByDefinitionId(definitionId);
        visibilityRepository.deleteAll(old);

        // 插入新规则
        LocalDateTime now = LocalDateTime.now();
        List<FormFieldVisibilityEntity> entities = rules.stream()
                .map(rule -> {
                    FormFieldVisibilityEntity e = new FormFieldVisibilityEntity();
                    e.setDefinition(definition);
                    e.setFieldKey(rule.fieldKey());
                    e.setRolePattern(rule.rolePattern());
                    e.setOrgId(rule.orgId());
                    e.setVisible(rule.visible());
                    e.setReadonly(rule.readonly());
                    e.setHidden(rule.hidden());
                    e.setCreatedAt(now);
                    return e;
                })
                .toList();
        visibilityRepository.saveAll(entities);

        // 清除缓存
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    /**
     * 保存条件规则
     */
    @Transactional
    public void saveConditionRules(Long definitionId, List<ConditionRuleDto> rules) {
        FormDefinitionRegistryEntity definition = findById(definitionId);

        // 删除旧规则
        List<FormFieldConditionEntity> old = conditionRepository.findByDefinitionId(definitionId);
        conditionRepository.deleteAll(old);

        // 插入新规则
        LocalDateTime now = LocalDateTime.now();
        List<FormFieldConditionEntity> entities = rules.stream()
                .map(rule -> {
                    FormFieldConditionEntity e = new FormFieldConditionEntity();
                    e.setDefinition(definition);
                    e.setSourceField(rule.sourceField());
                    e.setOperator(rule.operator());
                    e.setTargetValue(rule.targetValue());
                    e.setAction(rule.action());
                    e.setTargetField(rule.targetField());
                    e.setDisplayOrder(rule.displayOrder());
                    e.setCreatedAt(now);
                    return e;
                })
                .toList();
        conditionRepository.saveAll(entities);

        // 清除缓存
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    /**
     * 获取可见性规则
     */
    @Transactional(readOnly = true)
    public List<FormFieldVisibilityEntity> getVisibilityRules(Long definitionId) {
        return visibilityRepository.findByDefinitionId(definitionId);
    }

    /**
     * 获取条件规则
     */
    @Transactional(readOnly = true)
    public List<FormFieldConditionEntity> getConditionRules(Long definitionId) {
        return conditionRepository.findByDefinitionId(definitionId);
    }

    /**
     * 保存跨字段验证规则
     */
    @Transactional
    public void saveCrossFieldRules(Long definitionId, List<CrossFieldRuleDto> rules) {
        FormDefinitionRegistryEntity definition = findById(definitionId);
        List<CrossFieldValidationRuleEntity> old = crossFieldRuleRepository
                .findByDefinitionIdOrderByPriorityAsc(definitionId);
        crossFieldRuleRepository.deleteAll(old);

        LocalDateTime now = LocalDateTime.now();
        List<CrossFieldValidationRuleEntity> entities = rules.stream()
                .map(r -> {
                    CrossFieldValidationRuleEntity e = new CrossFieldValidationRuleEntity();
                    e.setDefinition(definition);
                    e.setScope(definition.getScope());
                    e.setSourceField(r.fieldA());
                    e.setOperator(r.operator());
                    e.setTargetField(r.fieldB());
                    e.setTargetValue(r.targetValue());
                    e.setErrorMessage(r.errorMessage());
                    e.setPriority(r.priority());
                    e.setCreatedAt(now);
                    return e;
                }).toList();
        crossFieldRuleRepository.saveAll(entities);
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    /**
     * 获取跨字段验证规则
     */
    @Transactional(readOnly = true)
    public List<CrossFieldValidationRuleEntity> getCrossFieldRules(Long definitionId) {
        return crossFieldRuleRepository.findByDefinitionIdOrderByPriorityAsc(definitionId);
    }

    /**
     * 保存租户字段覆盖
     */
    @Transactional
    public void saveTenantOverrides(Long definitionId, List<TenantOverrideDto> overrides) {
        FormDefinitionRegistryEntity definition = findById(definitionId);
        Long orgId = definition.getOrgId() != null ? definition.getOrgId() : 0L;
        List<TenantFormFieldOverrideEntity> old = tenantOverrideRepository
                .findByDefinitionIdAndOrgId(definitionId, orgId);
        tenantOverrideRepository.deleteAll(old);

        LocalDateTime now = LocalDateTime.now();
        List<TenantFormFieldOverrideEntity> entities = overrides.stream()
                .filter(o -> o.fieldKey() != null && !o.fieldKey().isBlank())
                .map(o -> {
                    TenantFormFieldOverrideEntity e = new TenantFormFieldOverrideEntity();
                    e.setDefinition(definition);
                    e.setOrgId(orgId);
                    e.setFieldKey(o.fieldKey());
                    e.setOverrideType(o.overrideType());
                    e.setOverrideValue(o.overrideValue());
                    e.setCreatedAt(now);
                    return e;
                }).toList();
        tenantOverrideRepository.saveAll(entities);
        adaptiveFormService.invalidateCache(definition.getScope(), definition.getOrgId());
    }

    /**
     * 获取租户字段覆盖
     */
    @Transactional(readOnly = true)
    public List<TenantFormFieldOverrideEntity> getTenantOverrides(Long definitionId) {
        FormDefinitionRegistryEntity definition = findById(definitionId);
        Long orgId = definition.getOrgId() != null ? definition.getOrgId() : 0L;
        return tenantOverrideRepository.findByDefinitionIdAndOrgId(definitionId, orgId);
    }

    private String toJson(Map<String, Object> schema) {
        try {
            return objectMapper.writeValueAsString(schema);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid schema JSON: " + e.getMessage());
        }
    }

    // DTO 记录

    public record CreateFormDefinitionRequest(
            String scope,
            String scopeLabel,
            Long orgId,
            Map<String, Object> schema
    ) {}

    public record UpdateFormDefinitionRequest(
            String scopeLabel,
            Map<String, Object> schema
    ) {}

    public record VisibilityRuleDto(
            String fieldKey,
            String rolePattern,
            Long orgId,
            boolean visible,
            boolean readonly,
            boolean hidden
    ) {}

    public record ConditionRuleDto(
            String sourceField,
            String operator,
            String targetValue,
            String action,
            String targetField,
            int displayOrder
    ) {}

    public record CrossFieldRuleDto(
            String fieldA,
            String operator,
            String fieldB,
            String targetValue,
            String errorMessage,
            int priority
    ) {}

    public record TenantOverrideDto(
            String fieldKey,
            String overrideType,
            String overrideValue
    ) {}
}
