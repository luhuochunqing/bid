package com.xiyu.bid.formengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.formengine.application.*;
import com.xiyu.bid.formengine.domain.*;
import com.xiyu.bid.formengine.infrastructure.persistence.CrossFieldValidationRuleRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldConditionRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.FormFieldVisibilityRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormDefinitionRegistryEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.CrossFieldValidationRuleEntity;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.FormFieldVisibilityEntity;
import com.xiyu.bid.workflowform.domain.FormFieldDefinition;
import com.xiyu.bid.workflowform.domain.FormFieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AdaptiveFormService 单元测试（使用 Mock）。
 * 测试 resolve()、validate()、submit() 流程。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("AdaptiveFormService")
class AdaptiveFormServiceTest {

    @Mock
    private FormDefinitionRegistryRepository definitionRepository;
    @Mock
    private FormFieldVisibilityRepository visibilityRepository;
    @Mock
    private FormFieldConditionRepository conditionRepository;
    @Mock
    private CrossFieldValidationRuleRepository crossFieldRuleRepository;
    @Mock
    private FormSchemaParser schemaParser;
    @Mock
    private UserRoleResolver userRoleResolver;
    @Mock
    private TenantOverrideService tenantOverrideService;
    @Mock
    private FormSubmissionAuditService auditService;
    @Mock
    private FormSubmissionRouter submissionRouter;

    private AdaptiveFormService service;
    private ConditionEvaluator conditionEvaluator;
    private CrossFieldValidator crossFieldValidator;
    private RoleBasedFieldFilter roleBasedFieldFilter;

    private static final String SCOPE = "tender.entry";
    private static final String USERNAME = "admin";
    private static final Long ORG_ID = null;

    @BeforeEach
    void setUp() {
        conditionEvaluator = new ConditionEvaluator();
        crossFieldValidator = new CrossFieldValidator();
        roleBasedFieldFilter = new RoleBasedFieldFilter();
        FormFieldValidator fieldValidator = new FormFieldValidator();
        VisibilityApplicator visibilityApplicator = new VisibilityApplicator(conditionEvaluator);

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOps = mock(ValueOperations.class);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

        ObjectMapper objectMapper = new ObjectMapper();

        service = new AdaptiveFormService(
                definitionRepository, visibilityRepository, conditionRepository,
                crossFieldRuleRepository, schemaParser,
                crossFieldValidator, roleBasedFieldFilter, userRoleResolver,
                tenantOverrideService, auditService, fieldValidator,
                visibilityApplicator, submissionRouter,
                objectMapper,
                Optional.of(redisTemplate)
        );
    }

    // ==================== resolve() Tests ====================

    @Nested
    @DisplayName("resolve()")
    class Resolve {

        @Test
        @DisplayName("成功解析全局定义")
        void resolve_globalDefinition() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯手工录入", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true),
                    new FormFieldDefinition("budget", "预算", FormFieldType.CURRENCY, false)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(visibilityRepository.findByDefinitionId(1L)).thenReturn(List.of());
            when(conditionRepository.findByDefinitionId(1L)).thenReturn(List.of());
            when(userRoleResolver.resolveRoles(USERNAME)).thenReturn(Set.of("admin"));
            when(tenantOverrideService.applyOverrides(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            ResolvedForm result = service.resolve(SCOPE, USERNAME, ORG_ID);

            assertThat(result.scope()).isEqualTo(SCOPE);
            assertThat(result.scopeLabel()).isEqualTo("标讯手工录入");
            assertThat(result.fields()).hasSize(2);
            assertThat(result.version()).isEqualTo(1);
        }

        @Test
        @DisplayName("租户定义优先于全局定义")
        void resolve_tenantOverridesGlobal() {
            FormDefinitionRegistryEntity globalEntity = mockDef(1L, SCOPE, "标讯手工录入", 1);
            FormDefinitionRegistryEntity tenantEntity = mockDef(2L, SCOPE, "租户标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true)
            );

            when(definitionRepository.findByScopeAndOrgIdAndEnabledTrue(SCOPE, 100L))
                    .thenReturn(Optional.of(tenantEntity));
            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(globalEntity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(visibilityRepository.findByDefinitionId(2L)).thenReturn(List.of());
            when(conditionRepository.findByDefinitionId(2L)).thenReturn(List.of());
            when(userRoleResolver.resolveRoles(USERNAME)).thenReturn(Set.of("admin"));
            when(tenantOverrideService.applyOverrides(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            ResolvedForm result = service.resolve(SCOPE, USERNAME, 100L);

            assertThat(result.scopeLabel()).isEqualTo("租户标讯");
        }

        @Test
        @DisplayName("scope 不存在抛出异常")
        void resolve_notFound() {
            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue("nonexistent"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.resolve("nonexistent", USERNAME, ORG_ID))
                    .isInstanceOf(AdaptiveFormService.FormDefinitionNotFoundException.class)
                    .hasMessageContaining("nonexistent");
        }

        @Test
        @DisplayName("resolve 应用角色过滤")
        void resolve_appliesRoleFilter() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, false),
                    new FormFieldDefinition("budget", "预算", FormFieldType.CURRENCY, false)
            );
            List<FormFieldVisibilityEntity> visibilityEntities = List.of(
                    mockVis(1L, entity, "budget", "staff", false, false, true)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(visibilityRepository.findByDefinitionId(1L)).thenReturn(visibilityEntities);
            when(conditionRepository.findByDefinitionId(1L)).thenReturn(List.of());
            when(userRoleResolver.resolveRoles("staff")).thenReturn(Set.of("staff"));
            when(tenantOverrideService.applyOverrides(any(), any())).thenAnswer(inv -> inv.getArgument(0));

            ResolvedForm result = service.resolve(SCOPE, "staff", ORG_ID);

            assertThat(result.fields()).hasSize(2);
            assertThat(result.fields().stream()
                    .filter(f -> f.key().equals("budget"))
                    .findFirst().get().hidden()).isTrue();
        }
    }

    // ==================== validate() Tests ====================

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("必填字段为空 → 验证失败")
        void validate_missingRequired() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenReturn(List.of());

            ValidationResult result = service.validate(SCOPE, Map.of());

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("title") && e.contains("必填"));
        }

        @Test
        @DisplayName("有效数据 → 验证通过")
        void validate_validData() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenReturn(List.of());

            ValidationResult result = service.validate(SCOPE, Map.of("title", "测试标讯"));

            assertThat(result.valid()).isTrue();
            assertThat(result.errors()).isEmpty();
        }

        @Test
        @DisplayName("跨字段验证失败 → 返回错误")
        void validate_crossFieldFails() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("status", "状态", FormFieldType.TEXT, false),
                    new FormFieldDefinition("prev_status", "上次状态", FormFieldType.TEXT, false)
            );
            CrossFieldValidationRuleEntity crossRule = mockCrossRule(entity, "status", "equals", "prev_status", null, "状态必须与上次相同");

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(visibilityRepository.findByDefinitionId(1L)).thenReturn(List.of());
            when(conditionRepository.findByDefinitionId(1L)).thenReturn(List.of());
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenReturn(List.of(crossRule));

            // status="draft" != prev_status="published" → validation fails
            ValidationResult result = service.validate(SCOPE, Map.of("status", "draft", "prev_status", "published"));

            assertThat(result.valid()).isFalse();
            assertThat(result.errors()).anyMatch(e -> e.contains("状态必须与上次相同"));
        }
    }

    // ==================== submit() Tests ====================

    @Nested
    @DisplayName("submit()")
    class Submit {

        @Test
        @DisplayName("验证通过 → 提交成功")
        void submit_success() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenReturn(List.of());
            when(submissionRouter.dispatch(eq(SCOPE), any(), eq(USERNAME)))
                    .thenReturn(SubmitResult.ok());
            doNothing().when(auditService).logSuccess(any(), any(), any(), any());

            SubmitResult result = service.submit(SCOPE, Map.of("title", "测试标讯"), USERNAME);

            assertThat(result.success()).isTrue();
            verify(submissionRouter).dispatch(eq(SCOPE), any(), eq(USERNAME));
            verify(auditService).logSuccess(eq(SCOPE), any(), eq(USERNAME), eq(ORG_ID));
        }

        @Test
        @DisplayName("验证失败 → 返回错误")
        void submit_validationFails() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenReturn(List.of());

            SubmitResult result = service.submit(SCOPE, Map.of(), USERNAME);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("验证失败");
            verify(auditService).logValidationFailure(eq(SCOPE), any(), eq(USERNAME), eq(ORG_ID), anyString());
        }

        @Test
        @DisplayName("处理异常 → 返回错误")
        void submit_processingError() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, false)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenThrow(new RuntimeException("Database error"));

            SubmitResult result = service.submit(SCOPE, Map.of("title", "Test"), USERNAME);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("异常");
            verify(auditService).logProcessingError(eq(SCOPE), any(), eq(USERNAME), eq(ORG_ID), anyString());
        }

        @Test
        @DisplayName("Router 业务失败 → 记录验证失败日志")
        void submit_routerReturnsFailure_logsValidationFailure() {
            FormDefinitionRegistryEntity entity = mockDef(1L, SCOPE, "标讯", 1);
            List<FormFieldDefinition> fields = List.of(
                    new FormFieldDefinition("title", "标题", FormFieldType.TEXT, true)
            );

            when(definitionRepository.findByScopeAndOrgIdIsNullAndEnabledTrue(SCOPE))
                    .thenReturn(Optional.of(entity));
            when(schemaParser.parseFields(anyString())).thenReturn(fields);
            when(crossFieldRuleRepository.findByDefinitionIdAndScopeOrderByPriorityAsc(1L, SCOPE))
                    .thenReturn(List.of());
            when(submissionRouter.dispatch(eq(SCOPE), any(), eq(USERNAME)))
                    .thenReturn(SubmitResult.failure("未知表单类型: resource.expense"));

            SubmitResult result = service.submit(SCOPE, Map.of("title", "测试标讯"), USERNAME);

            assertThat(result.success()).isFalse();
            assertThat(result.message()).contains("未知表单类型");
            verify(auditService).logValidationFailure(eq(SCOPE), any(), eq(USERNAME), eq(ORG_ID), anyString());
        }
    }

    // ==================== invalidateCache ====================

    @Nested
    @DisplayName("invalidateCache()")
    class InvalidateCache {

        @Test
        @DisplayName("删除缓存键")
        void invalidateCache_deletesKey() {
            StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
            ValueOperations<String, String> valueOps = mock(ValueOperations.class);
            lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);

            ObjectMapper objectMapper = new ObjectMapper();
            FormFieldValidator fieldValidator = new FormFieldValidator();
            VisibilityApplicator visibilityApplicator = new VisibilityApplicator(conditionEvaluator);
            AdaptiveFormService svcWithCache = new AdaptiveFormService(
                    definitionRepository, visibilityRepository, conditionRepository,
                    crossFieldRuleRepository, schemaParser,
                    crossFieldValidator, roleBasedFieldFilter, userRoleResolver,
                    tenantOverrideService, auditService, fieldValidator,
                    visibilityApplicator, submissionRouter,
                    objectMapper,
                    Optional.of(redisTemplate)
            );

            svcWithCache.invalidateCache(SCOPE, 100L);

            verify(redisTemplate).delete("form:def:tender.entry:100");
            verify(redisTemplate).delete("form:def:tender.entry:global");
        }
    }

    // ==================== Helpers ====================

    private FormDefinitionRegistryEntity mockDef(Long id, String scope, String scopeLabel, int version) {
        FormDefinitionRegistryEntity entity = mock(FormDefinitionRegistryEntity.class);
        lenient().when(entity.getId()).thenReturn(id);
        lenient().when(entity.getScope()).thenReturn(scope);
        lenient().when(entity.getScopeLabel()).thenReturn(scopeLabel);
        lenient().when(entity.getVersion()).thenReturn(version);
        lenient().when(entity.getSchemaJson()).thenReturn("{\"fields\":[]}");
        return entity;
    }

    private FormFieldVisibilityEntity mockVis(
            Long id, FormDefinitionRegistryEntity def, String fieldKey,
            String rolePattern, boolean visible, boolean readonly, boolean hidden) {
        FormFieldVisibilityEntity entity = mock(FormFieldVisibilityEntity.class);
        lenient().when(entity.getId()).thenReturn(id);
        lenient().when(entity.getDefinition()).thenReturn(def);
        lenient().when(entity.getFieldKey()).thenReturn(fieldKey);
        lenient().when(entity.getRolePattern()).thenReturn(rolePattern);
        lenient().when(entity.getOrgId()).thenReturn(null);
        lenient().when(entity.getVisible()).thenReturn(visible);
        lenient().when(entity.getReadonly()).thenReturn(readonly);
        lenient().when(entity.getHidden()).thenReturn(hidden);
        return entity;
    }

    private CrossFieldValidationRuleEntity mockCrossRule(
            FormDefinitionRegistryEntity def, String sourceField,
            String operator, String targetField, String targetValue, String errorMsg) {
        CrossFieldValidationRuleEntity entity = mock(CrossFieldValidationRuleEntity.class);
        lenient().when(entity.getId()).thenReturn(1L);
        lenient().when(entity.getDefinition()).thenReturn(def);
        lenient().when(entity.getScope()).thenReturn(SCOPE);
        lenient().when(entity.getSourceField()).thenReturn(sourceField);
        lenient().when(entity.getOperator()).thenReturn(operator);
        lenient().when(entity.getTargetField()).thenReturn(targetField);
        lenient().when(entity.getTargetValue()).thenReturn(targetValue);
        lenient().when(entity.getErrorMessage()).thenReturn(errorMsg);
        lenient().when(entity.getPriority()).thenReturn(0);
        return entity;
    }
}
