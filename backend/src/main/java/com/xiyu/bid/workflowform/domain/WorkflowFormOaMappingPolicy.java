package com.xiyu.bid.workflowform.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WorkflowFormOaMappingPolicy {

    private static final List<String> SOURCE_PREFIXES = List.of("formData.", "context.", "applicant.");

    private WorkflowFormOaMappingPolicy() {
    }

    public static ValidationResult validate(Map<String, Object> mapping) {
        List<String> errors = new ArrayList<>();
        if (WorkflowFormSchemaPolicy.text(mapping == null ? null : mapping.get("workflowCode")).isBlank()) {
            errors.add("OA 流程编码不能为空");
        }
        Set<String> targets = new HashSet<>();
        for (Map<String, Object> field : mainFields(mapping)) {
            validateMappingField(field, targets, errors);
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> mainFields(Map<String, Object> mapping) {
        Object fields = mapping == null ? null : mapping.get("mainFields");
        if (!(fields instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static void validateMappingField(Map<String, Object> field, Set<String> targets, List<String> errors) {
        String source = WorkflowFormSchemaPolicy.text(field.get("source"));
        String target = WorkflowFormSchemaPolicy.text(field.get("target"));
        if (source.isBlank()) {
            errors.add("OA 映射 source 不能为空");
        } else if (!safeSource(source)) {
            errors.add("OA 映射 source 不在白名单内: " + source);
        }
        if (target.isBlank()) {
            errors.add("OA 映射 target 不能为空");
        } else if (!targets.add(target)) {
            errors.add("OA 字段 target 不得重复: " + target);
        }
    }

    static boolean safeSource(String source) {
        return SOURCE_PREFIXES.stream().anyMatch(source::startsWith);
    }
}
