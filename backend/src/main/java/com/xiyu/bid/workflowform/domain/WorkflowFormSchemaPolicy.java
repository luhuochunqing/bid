package com.xiyu.bid.workflowform.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class WorkflowFormSchemaPolicy {

    private WorkflowFormSchemaPolicy() {
    }

    public static ValidationResult validate(Map<String, Object> schema) {
        List<String> errors = new ArrayList<>();
        List<Map<String, Object>> fields = fields(schema);
        if (fields.isEmpty()) {
            errors.add("表单至少需要一个字段");
            return ValidationResult.invalid(errors);
        }
        Set<String> keys = new HashSet<>();
        for (Map<String, Object> field : fields) {
            validateField(field, keys, errors);
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> fields(Map<String, Object> schema) {
        Object fields = schema == null ? null : schema.get("fields");
        if (!(fields instanceof List<?> list)) {
            return List.of();
        }
        return list.stream()
                .filter(Map.class::isInstance)
                .map(item -> (Map<String, Object>) item)
                .toList();
    }

    private static void validateField(Map<String, Object> field, Set<String> keys, List<String> errors) {
        String key = text(field.get("key"));
        String type = text(field.get("type")).toUpperCase(Locale.ROOT);
        if (key.isBlank()) {
            errors.add("字段 key 不能为空");
        } else if (!keys.add(key)) {
            errors.add("字段 key 不得重复: " + key);
        }
        if (!supportedType(type)) {
            errors.add("字段类型不支持: " + text(field.get("type")));
        }
        if ("SELECT".equals(type) && options(field).isEmpty()) {
            errors.add("下拉字段必须配置选项: " + key);
        }
    }

    private static boolean supportedType(String type) {
        if (type.isBlank()) {
            return false;
        }
        for (FormFieldType fieldType : FormFieldType.values()) {
            if (fieldType.name().equals(type)) {
                return true;
            }
        }
        return false;
    }

    private static List<?> options(Map<String, Object> field) {
        Object options = field.get("options");
        return options instanceof List<?> list ? list : List.of();
    }

    static String text(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
