package com.xiyu.bid.workflowform.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FormSchemaValidator {

    private static final List<String> QUALIFICATION_BORROW_FIELDS = List.of(
            "qualificationId",
            "borrower",
            "department",
            "projectId",
            "purpose",
            "expectedReturnDate"
    );

    private FormSchemaValidator() {
    }

    public static ValidationResult validate(FormSchema schema) {
        List<String> errors = new ArrayList<>();
        Set<String> keys = new HashSet<>();
        for (FormFieldDefinition field : schema.fields()) {
            if (!keys.add(field.key())) {
                errors.add("字段 key 不得重复: " + field.key());
            }
        }
        if (schema.businessType() == FormBusinessType.QUALIFICATION_BORROW) {
            validateQualificationBorrowFields(schema, errors);
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }

    private static ValidationResult validateQualificationBorrowFields(FormSchema schema, List<String> errors) {
        Set<String> actual = new HashSet<>();
        for (FormFieldDefinition field : schema.fields()) {
            actual.add(field.key());
        }
        for (String required : QUALIFICATION_BORROW_FIELDS) {
            if (!actual.contains(required)) {
                errors.add("资质借阅表单缺少业务字段: " + required);
            }
        }
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }
}
