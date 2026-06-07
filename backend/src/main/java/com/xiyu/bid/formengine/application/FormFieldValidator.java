package com.xiyu.bid.formengine.application;

import com.xiyu.bid.formengine.domain.ValidationResult;
import com.xiyu.bid.workflowform.domain.FormFieldDefinition;
import com.xiyu.bid.workflowform.domain.FormFieldType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 单字段级验证（不含跨字段验证）。
 * 职责：必填、数值范围、字符串长度。
 */
@Slf4j
@Service
public class FormFieldValidator {

    public ValidationResult validateFields(List<FormFieldDefinition> fields, Map<String, Object> formData) {
        List<String> errors = new ArrayList<>();

        for (FormFieldDefinition field : fields) {
            if (field.hidden().orElse(false)) continue;

            Object value = formData.get(field.key());

            if (field.required() && isEmpty(value)) {
                errors.add(msg(field, "为必填项"));
                continue;
            }
            if (isEmpty(value)) continue;

            if (isNumericField(field.type())) {
                double numValue = toDouble(value);
                if (field.min().isPresent() && numValue < field.min().get()) {
                    errors.add(msg(field, "最小值为 " + field.min().get()));
                }
                if (field.max().isPresent() && numValue > field.max().get()) {
                    errors.add(msg(field, "最大值为 " + field.max().get()));
                }
            }

            if (isTextField(field.type())) {
                String strValue = String.valueOf(value);
                if (field.minLength().isPresent() && strValue.length() < field.minLength().get()) {
                    errors.add(msg(field, "最小长度为 " + field.minLength().get() + " 字"));
                }
                if (field.maxLength().isPresent() && strValue.length() > field.maxLength().get()) {
                    errors.add(msg(field, "最大长度为 " + field.maxLength().get() + " 字"));
                }
            }

            // customRegex 验证（优先级最高）
            if (field.customRegex().isPresent() && !isEmpty(value)) {
                String regex = field.customRegex().get();
                try {
                    if (!String.valueOf(value).matches(regex)) {
                        errors.add(msg(field, "格式不符合要求"));
                    }
                } catch (Exception e) {
                    log.warn("Invalid regex for field {}: {}", field.key(), regex);
                }
            }
        }

        return errors.isEmpty()
                ? ValidationResult.success()
                : ValidationResult.failure(errors);
    }

    private String msg(FormFieldDefinition field, String defaultSuffix) {
        return field.errorMessage().isPresent()
                ? field.errorMessage().get()
                : "[" + field.key() + "] " + field.label() + " " + defaultSuffix;
    }

    private boolean isEmpty(Object value) {
        if (value == null) return true;
        if (value instanceof String s) return s.isBlank();
        return false;
    }

    private boolean isNumericField(FormFieldType type) {
        return type == FormFieldType.NUMBER
                || type == FormFieldType.CURRENCY
                || type == FormFieldType.PERCENT;
    }

    private boolean isTextField(FormFieldType type) {
        return type == FormFieldType.TEXT
                || type == FormFieldType.TEXTAREA
                || type == FormFieldType.PHONE
                || type == FormFieldType.EMAIL;
    }

    private double toDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        return Double.parseDouble(String.valueOf(value));
    }
}
