package com.xiyu.bid.workflowform.domain;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 字段定义 record，支持所有 20+ 字段类型及增强验证规则。
 * 为保证向后兼容，原有 4 个参数（key/label/type/required）保留，
 * 新增字段以 Optional / List 形式追加，不影响现有调用方。
 */
public record FormFieldDefinition(
        String key,
        String label,
        FormFieldType type,
        boolean required,
        // --- 增强属性（M1 扩展，V140） ---
        Optional<String> placeholder,
        Optional<String> content,
        Optional<Integer> rows,
        Optional<Double> min,
        Optional<Double> max,
        Optional<Integer> minLength,
        Optional<Integer> maxLength,
        Optional<String> customRegex,
        Optional<String> errorMessage,
        Optional<List<FieldOption>> options,
        Optional<Integer> limit,
        Optional<String> accept,
        Optional<Boolean> hidden,
        Optional<Boolean> readonly,
        Optional<List<TableColumn>> columns,
        Optional<Integer> minRows,
        Optional<Integer> maxRows
) {

    public FormFieldDefinition {
        if (placeholder == null) placeholder = Optional.empty();
        if (content == null) content = Optional.empty();
        if (rows == null) rows = Optional.empty();
        if (min == null) min = Optional.empty();
        if (max == null) max = Optional.empty();
        if (minLength == null) minLength = Optional.empty();
        if (maxLength == null) maxLength = Optional.empty();
        if (customRegex == null) customRegex = Optional.empty();
        if (errorMessage == null) errorMessage = Optional.empty();
        if (options == null) options = Optional.empty();
        if (limit == null) limit = Optional.empty();
        if (accept == null) accept = Optional.empty();
        if (hidden == null) hidden = Optional.empty();
        if (readonly == null) readonly = Optional.empty();
        if (columns == null) columns = Optional.empty();
        if (minRows == null) minRows = Optional.empty();
        if (maxRows == null) maxRows = Optional.empty();
    }

    /** 向后兼容构造函数（4 参数，原有调用方无需变更） */
    public FormFieldDefinition(String key, String label, FormFieldType type, boolean required) {
        this(key, label, type, required,
                Optional.<String>empty(), Optional.<String>empty(), Optional.<Integer>empty(),
                Optional.<Double>empty(), Optional.<Double>empty(),
                Optional.<Integer>empty(), Optional.<Integer>empty(),
                Optional.<String>empty(), Optional.<String>empty(),
                Optional.<List<FieldOption>>empty(), Optional.<Integer>empty(),
                Optional.<String>empty(), Optional.<Boolean>empty(),
                Optional.<Boolean>empty(), Optional.<List<TableColumn>>empty(),
                Optional.<Integer>empty(), Optional.<Integer>empty());
    }

    /** 从 Map 构造（用于 JSON 反序列化） */
    @SuppressWarnings("unchecked")
    public static FormFieldDefinition fromMap(Map<String, Object> map) {
        String key = trim(map.get("key"));
        String label = trim(map.get("label"));
        String typeStr = trim(map.get("type"));
        boolean required = Boolean.TRUE.equals(map.get("required"));

        FormFieldType type = FormFieldType.TEXT;
        if (typeStr != null) {
            try {
                type = FormFieldType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException ignored) {
                type = FormFieldType.TEXT;
            }
        }

        return new FormFieldDefinition(
                key != null ? key : "unknown",
                label != null ? label : key,
                type,
                required,
                optStr(map, "placeholder"),
                optStr(map, "content"),
                optInt(map, "rows"),
                optDouble(map, "min"),
                optDouble(map, "max"),
                optInt(map, "minLength"),
                optInt(map, "maxLength"),
                optStr(map, "customRegex"),
                optStr(map, "errorMessage"),
                optList(map, "options"),
                optInt(map, "limit"),
                optStr(map, "accept"),
                optBool(map, "hidden"),
                optBool(map, "readonly"),
                optTableColumns(map, "columns"),
                optInt(map, "minRows"),
                optInt(map, "maxRows")
        );
    }

    private static String trim(Object value) {
        if (value == null) return null;
        String s = String.valueOf(value).trim();
        return s.isEmpty() ? null : s;
    }

    private static Optional<String> optStr(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return Optional.empty();
        return Optional.of(String.valueOf(val).trim());
    }

    private static Optional<Boolean> optBool(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return Optional.empty();
        if (val instanceof Boolean b) return Optional.of(b);
        if (val instanceof String s) return Optional.of(Boolean.parseBoolean(s));
        return Optional.empty();
    }

    private static Optional<Integer> optInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return Optional.empty();
        if (val instanceof Number n) return Optional.of(n.intValue());
        try {
            return Optional.of(Integer.parseInt(String.valueOf(val)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<Double> optDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return Optional.empty();
        if (val instanceof Number n) return Optional.of(n.doubleValue());
        try {
            return Optional.of(Double.parseDouble(String.valueOf(val)));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    @SuppressWarnings("unchecked")
    private static Optional<List<FieldOption>> optList(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (!(val instanceof List<?> list)) return Optional.empty();
        return Optional.of(list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(m -> new FieldOption(
                        trim(m.get("label")),
                        trim(m.get("value")),
                        trim(m.get("disabled"))))
                .toList());
    }

    @SuppressWarnings("unchecked")
    private static Optional<List<TableColumn>> optTableColumns(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (!(val instanceof List<?> list)) return Optional.empty();
        return Optional.of(list.stream()
                .filter(Map.class::isInstance)
                .map(Map.class::cast)
                .map(m -> {
                    String colType = trim(m.get("type"));
                    FormFieldType ft = FormFieldType.TEXT;
                    if (colType != null) {
                        try { ft = FormFieldType.valueOf(colType.toUpperCase()); }
                        catch (IllegalArgumentException ignored) {}
                    }
                    return new TableColumn(trim(m.get("key")), trim(m.get("label")), ft, Boolean.TRUE.equals(m.get("required")));
                })
                .toList());
    }

    public record FieldOption(String label, String value, String disabled) {}
    public record TableColumn(String key, String label, FormFieldType type, boolean required) {}
}
