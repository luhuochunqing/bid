package com.xiyu.bid.workflowform.domain;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record FormAttachmentValue(
        String fileName,
        String storagePath,
        String fileUrl,
        String contentType,
        Long size
) {

    public FormAttachmentValue {
        fileName = normalize(fileName);
        storagePath = normalize(storagePath);
        fileUrl = normalize(fileUrl);
        contentType = normalize(contentType);
    }

    public static ValidationResult validateFieldValue(Object rawValue, boolean required, String requiredMessage) {
        List<String> errors = new ArrayList<>();
        List<?> items = toItems(rawValue);
        if (items == null) {
            errors.add(requiredMessage);
            return ValidationResult.invalid(errors);
        }
        if (items.isEmpty()) {
            if (required) {
                errors.add(requiredMessage);
            }
            return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
        }
        for (int index = 0; index < items.size(); index++) {
            validateItem(items.get(index), index + 1, errors);
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private static List<?> toItems(Object rawValue) {
        if (rawValue == null) {
            return List.of();
        }
        if (rawValue instanceof List<?> list) {
            return List.copyOf(list);
        }
        Class<?> valueClass = rawValue.getClass();
        if (valueClass.isArray()) {
            int length = Array.getLength(rawValue);
            List<Object> values = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                values.add(Array.get(rawValue, index));
            }
            return List.copyOf(values);
        }
        return null;
    }

    private static void validateItem(Object item, int position, List<String> errors) {
        if (!(item instanceof Map<?, ?> map)) {
            errors.add("附件第 " + position + " 项格式不正确");
            return;
        }
        FormAttachmentValue value = fromMap(map);
        if (value.fileName() == null) {
            errors.add("附件第 " + position + " 项缺少文件名");
        }
        if (value.storagePath() == null && value.fileUrl() == null) {
            errors.add("附件第 " + position + " 项缺少存储路径或文件地址");
        }
        if (value.size() != null && value.size() < 0) {
            errors.add("附件第 " + position + " 项文件大小不能为负数");
        }
    }

    private static FormAttachmentValue fromMap(Map<?, ?> map) {
        return new FormAttachmentValue(
                stringValue(map.get("fileName")),
                stringValue(map.get("storagePath")),
                stringValue(map.get("fileUrl")),
                stringValue(map.get("contentType")),
                sizeValue(map.get("size"))
        );
    }

    private static String stringValue(Object value) {
        return value instanceof String text ? text : null;
    }

    private static Long sizeValue(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return null;
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
