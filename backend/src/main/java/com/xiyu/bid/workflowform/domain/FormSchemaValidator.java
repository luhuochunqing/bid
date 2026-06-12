package com.xiyu.bid.workflowform.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class FormSchemaValidator {

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
        return errors.isEmpty() ? ValidationResult.ok() : ValidationResult.invalid(errors);
    }
}
