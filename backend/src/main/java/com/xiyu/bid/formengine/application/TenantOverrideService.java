// Input: 解析后的表单 + 租户 ID
// Output: 应用租户覆盖后的表单
// Pos: Application 层
// 维护声明: 编排逻辑，覆盖规则在 domain/application 层.
package com.xiyu.bid.formengine.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiyu.bid.formengine.domain.ResolvedField;
import com.xiyu.bid.formengine.domain.ResolvedForm;
import com.xiyu.bid.formengine.infrastructure.persistence.FormDefinitionRegistryRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.TenantFormFieldOverrideRepository;
import com.xiyu.bid.formengine.infrastructure.persistence.entity.TenantFormFieldOverrideEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 租户字段覆盖服务。
 * 允许租户管理员覆盖特定字段的标签、必填、默认值、选项和隐藏状态。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantOverrideService {

    private final TenantFormFieldOverrideRepository overrideRepository;
    private final FormDefinitionRegistryRepository definitionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 应用租户字段覆盖到解析后的表单。
     *
     * @param form 原始解析表单
     * @param orgId 租户 ID
     * @return 应用覆盖后的新表单实例
     */
    @Transactional(readOnly = true)
    public ResolvedForm applyOverrides(ResolvedForm form, Long orgId) {
        if (orgId == null) {
            return form;
        }

        // 查找该租户的定义
        Optional<Long> definitionId = definitionRepository
                .findByScopeAndOrgIdAndEnabledTrue(form.scope(), orgId)
                .map(e -> e.getId());

        if (definitionId.isEmpty()) {
            // 没有租户特定定义，无需覆盖
            return form;
        }

        List<TenantFormFieldOverrideEntity> overrides = overrideRepository
                .findByDefinitionIdAndOrgId(definitionId.get(), orgId);

        if (overrides.isEmpty()) {
            return form;
        }

        // 按 fieldKey 分组覆盖规则
        Map<String, List<TenantFormFieldOverrideEntity>> byField = overrides.stream()
                .collect(Collectors.groupingBy(TenantFormFieldOverrideEntity::getFieldKey));

        List<ResolvedField> patchedFields = form.fields().stream()
                .map(field -> applyOverridesToField(field, byField.get(field.key())))
                .toList();

        return new ResolvedForm(form.scope(), form.scopeLabel(), patchedFields, form.version());
    }

    private ResolvedField applyOverridesToField(ResolvedField field, List<TenantFormFieldOverrideEntity> rules) {
        if (rules == null || rules.isEmpty()) {
            return field;
        }

        ResolvedField.Builder builder = ResolvedField.builder()
                .key(field.key())
                .label(field.label())
                .type(field.type())
                .required(field.required())
                .hidden(field.hidden())
                .readonly(field.readonly())
                .defaultValue(field.defaultValue())
                .options(field.options());

        for (TenantFormFieldOverrideEntity rule : rules) {
            applyOverride(builder, rule);
        }

        return builder.build();
    }

    private void applyOverride(ResolvedField.Builder builder, TenantFormFieldOverrideEntity rule) {
        String type = rule.getOverrideType();
        String value = rule.getOverrideValue();

        switch (type) {
            case "label" -> builder.label(value);
            case "required" -> builder.required(Boolean.parseBoolean(value));
            case "default_value" -> builder.defaultValue(parseDefaultValue(value));
            case "options" -> builder.options(parseOptions(value));
            case "hidden" -> builder.hidden(Boolean.parseBoolean(value));
            case "readonly" -> builder.readonly(Boolean.parseBoolean(value));
            default -> log.warn("Unknown override type: {}", type);
        }
    }

    private Object parseDefaultValue(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            return value;
        }
    }

    private Object parseOptions(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return objectMapper.readValue(value, Object.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
