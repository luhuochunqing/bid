package com.xiyu.bid.workflowform.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class FormSubmissionValidator {

    private FormSubmissionValidator() {
    }

    public static ValidationResult validateQualificationBorrow(Map<String, ?> values) {
        List<String> errors = new ArrayList<>();
        requirePresent(values, "qualificationId", "请选择资质", errors);
        requirePresent(values, "borrower", "请填写借用人", errors);
        requirePresent(values, "projectId", "请选择项目", errors);
        requirePresent(values, "purpose", "请填写用途", errors);
        requirePresent(values, "expectedReturnDate", "请选择预计归还日期", errors);
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    public static ValidationResult validate(FormSchema schema, Map<String, ?> values) {
        List<String> errors = new ArrayList<>();
        for (FormFieldDefinition field : schema.fields()) {
            if (field.type() == FormFieldType.ATTACHMENT) {
                appendAttachmentErrors(field, values.get(field.key()), errors);
            } else if (field.required()) {
                requirePresent(values, field.key(), "请填写" + field.label(), errors);
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private static ValidationResult requirePresent(Map<String, ?> values, String key, String message, List<String> errors) {
        Object value = values.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            errors.add(message);
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private static void appendAttachmentErrors(FormFieldDefinition field, Object value, List<String> errors) {
        ValidationResult result = FormAttachmentValue.validateFieldValue(value, field.required(), "请上传" + field.label());
        errors.addAll(result.errors());
    }
}
