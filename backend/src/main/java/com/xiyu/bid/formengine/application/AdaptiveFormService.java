// Input: scope / operatorUsername / orgId
// Output: 表单解析、验证、提交
// Pos: Application 层（编排层，调用 domain / infrastructure）
// 维护声明: 编排逻辑，核心规则在 domain / ConditionEvaluator.
package com.xiyu.bid.formengine.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.formengine.domain.CrossFieldValidationRule;
import com.xiyu.bid.formengine.domain.FieldVisibility;
import com.xiyu.bid.formengine.domain.FormFieldCondition;
import com.xiyu.bid.formengine.domain.ResolvedField;
import com.xiyu.bid.formengine.domain.ResolvedForm;
import com.xiyu.bid.formengine.domain.SubmitResult;
import com.xiyu.bid.formengine.domain.ValidationResult;
import com.xiyu.bid.formengine.infrastructure.persistence.CrossFieldValidationRuleRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldConditionRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldVisibilityRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import com.xiyu.bid.workflowform.domain.FormFieldDefinition;
import com.xiyu.bid.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 自适应表单运行时服务。
 * 负责：表单解析、可见性规则应用、验证、提交编排。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AdaptiveFormService {

    private static final String CACHE_KEY_PREFIX = "form:def:";
    private static final Duration CACHE_TTL = Duration.ofMinutes(5);

    private final FormDefinitionRegistryRepository definitionRepository;
    private final FormFieldVisibilityRepository visibilityRepository;
    private final FormFieldConditionRepository conditionRepository;
    private final CrossFieldValidationRuleRepository crossFieldRuleRepository;
    private final FormSchemaParser schemaParser;
    private final CrossFieldValidator crossFieldValidator;
    private final RoleBasedFieldFilter roleBasedFieldFilter;
    private final UserRoleResolver userRoleResolver;
    private final TenantOverrideService tenantOverrideService;
    private final FormSubmissionAuditService auditService;
    private final FormFieldValidator fieldValidator;
    private final VisibilityApplicator visibilityApplicator;
    private final FormSubmissionRouter submissionRouter;
    private final ObjectMapper objectMapper;
    private final Optional<StringRedisTemplate> redisTemplate;

    /**
     * 解析并返回激活的表单定义。
     * 优先使用租户特定定义，兜底全局定义。
     */
    @Transactional(readOnly = true)
    public ResolvedForm resolve(String scope, String operatorUsername, Long orgId) {
        String cacheKey = cacheKey(scope, orgId);
        Optional<ResolvedForm> cached = getFromCache(cacheKey);
        if (cached.isPresent()) {
            log.debug("Cache hit for form scope={}, orgId={}", scope, orgId);
            return cached.get();
        }

        FormDefinitionRegistryEntity definition = findActiveDefinition(scope, orgId)
                .orElseThrow(() -> new FormDefinitionNotFoundException(scope));

        List<FormFieldDefinition> fields = schemaParser.parseFields(definition.getSchemaJson());
        List<FieldVisibility> visibilityRules = loadVisibilityRules(definition.getId());
        List<FormFieldCondition> conditions = loadConditions(definition.getId());

        List<ResolvedField> resolvedFields = visibilityApplicator.apply(
                fields, visibilityRules, conditions, new HashMap<>());

        Set<String> userRoles = userRoleResolver.resolveRoles(operatorUsername);
        resolvedFields = roleBasedFieldFilter.apply(resolvedFields, visibilityRules, userRoles);

        ResolvedForm result = new ResolvedForm(
                scope,
                definition.getScopeLabel(),
                resolvedFields,
                definition.getVersion()
        );
        result = tenantOverrideService.applyOverrides(result, orgId);

        saveToCache(cacheKey, result);
        return result;
    }

    /**
     * 验证表单数据（包含单字段验证 + 跨字段验证）。
     */
    @Transactional(readOnly = true)
    public ValidationResult validate(String scope, Map<String, Object> formData) {
        FormDefinitionRegistryEntity definition = findActiveDefinition(scope, null)
                .orElseThrow(() -> new FormDefinitionNotFoundException(scope));

        List<FormFieldDefinition> fields = schemaParser.parseFields(definition.getSchemaJson());
        List<String> errors = new ArrayList<>(fieldValidator.validateFields(fields, formData).errors());

        List<CrossFieldValidationRule> crossRules = loadCrossFieldRules(definition.getId(), scope);
        errors.addAll(crossFieldValidator.validateAll(crossRules, formData));

        return errors.isEmpty() ? ValidationResult.success() : ValidationResult.failure(errors);
    }

    /**
     * 提交表单数据。
     * 验证通过后，路由到具体业务处理器（TenderCommandService / ProjectService 等）。
     */
    @Transactional
    public SubmitResult submit(String scope, Map<String, Object> formData, String operatorUsername) {
        Long orgId = null; // M3: resolve from JWT tenant claim
        log.info("Form submission: scope={}, operator={}", scope, operatorUsername);

        try {
            ValidationResult validation = validate(scope, formData);
            if (!validation.valid()) {
                log.warn("Form validation failed: scope={}, errors={}", scope, validation.errors());
                auditService.logValidationFailure(scope, formData, operatorUsername, orgId,
                        String.join("; ", validation.errors()));
                return SubmitResult.failure("表单验证失败: " + String.join("; ", validation.errors()));
            }

            // 路由到业务处理器
            SubmitResult result = submissionRouter.dispatch(scope, formData, operatorUsername);
            if (result.success()) {
                log.info("Form submitted successfully: scope={}, operator={}, dataKeys={}",
                        scope, operatorUsername, formData.keySet());
                auditService.logSuccess(scope, formData, operatorUsername, orgId);
            } else {
                log.warn("Form business processing failed: scope={}, message={}", scope, result.message());
                auditService.logValidationFailure(scope, formData, operatorUsername, orgId, result.message());
            }
            return result;
        } catch (Exception e) {
            log.error("Form processing error: scope={}, operator={}, error={}",
                    scope, operatorUsername, e.getMessage(), e);
            auditService.logProcessingError(scope, formData, operatorUsername, orgId, e.getMessage());
            return SubmitResult.failure("表单处理异常: " + e.getMessage());
        }
    }

    /**
     * 使缓存失效（发布时调用）。
     */
    public void invalidateCache(String scope, Long orgId) {
        String cacheKey = cacheKey(scope, orgId);
        redisTemplate.ifPresent(t -> {
            try {
                t.delete(cacheKey);
                log.debug("Cache invalidated: key={}", cacheKey);
            } catch (RuntimeException e) {
                log.warn("Failed to invalidate cache: key={}, error={}", cacheKey, e.getMessage());
            }
        });

        if (orgId != null) {
            invalidateCache(scope, null);
        }
    }

    private Optional<FormDefinitionRegistryEntity> findActiveDefinition(String scope, Long orgId) {
        if (orgId != null) {
            Optional<FormDefinitionRegistryEntity> tenant = definitionRepository
                    .findByScopeAndOrgIdAndEnabledTrue(scope, orgId);
            if (tenant.isPresent()) return tenant;
        }
        return definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(scope);
    }

    private List<FieldVisibility> loadVisibilityRules(Long definitionId) {
        return visibilityRepository.findByDefinitionId(definitionId).stream()
                .map(FormEngineMapper::toVisibility)
                .toList();
    }

    private List<FormFieldCondition> loadConditions(Long definitionId) {
        return conditionRepository.findByDefinitionId(definitionId).stream()
                .map(FormEngineMapper::toCondition)
                .toList();
    }

    private List<CrossFieldValidationRule> loadCrossFieldRules(Long definitionId, String scope) {
        return crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(definitionId, scope).stream()
                .map(FormEngineMapper::toCrossFieldRule)
                .toList();
    }

    private Optional<ResolvedForm> getFromCache(String cacheKey) {
        return redisTemplate.flatMap(t -> {
            try {
                String json = t.opsForValue().get(cacheKey);
                if (json == null) return Optional.empty();
                return Optional.of(objectMapper.readValue(json, ResolvedForm.class));
            } catch (JsonProcessingException e) {
                log.warn("Failed to deserialize cached form: {}", e.getMessage());
                return Optional.empty();
            } catch (Exception e) {
                log.debug("Redis unavailable, skipping cache: {}", e.getMessage());
                return Optional.empty();
            }
        });
    }

    private void saveToCache(String cacheKey, ResolvedForm form) {
        redisTemplate.ifPresent(t -> {
            try {
                t.opsForValue().set(cacheKey, objectMapper.writeValueAsString(form), CACHE_TTL);
            } catch (JsonProcessingException e) {
                log.warn("Failed to cache form: {}", e.getMessage());
            } catch (Exception e) {
                log.debug("Redis unavailable, skipping cache save: {}", e.getMessage());
            }
        });
    }

    private String cacheKey(String scope, Long orgId) {
        return CACHE_KEY_PREFIX + scope + ":" + (orgId != null ? orgId : "global");
    }

    public static class FormDefinitionNotFoundException extends ResourceNotFoundException {
        private final String scope;

        public FormDefinitionNotFoundException(String scope) {
            super("Form definition", scope);
            this.scope = scope;
        }

        public String getScope() {
            return scope;
        }
    }
}
