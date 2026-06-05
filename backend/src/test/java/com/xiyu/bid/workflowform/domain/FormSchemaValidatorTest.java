package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FormSchemaValidatorTest {

    @Test
    void rejects_duplicate_field_keys() {
        FormSchema schema = new FormSchema(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                List.of(
                        new FormFieldDefinition("qualificationId", "资质", FormFieldType.QUALIFICATION, true),
                        new FormFieldDefinition("qualificationId", "重复资质", FormFieldType.TEXT, true)
                )
        );

        ValidationResult result = FormSchemaValidator.validate(schema);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("字段 key 不得重复: qualificationId");
    }

    @Test
    void rejects_qualification_borrow_schema_without_required_business_fields() {
        FormSchema schema = new FormSchema(
                "QUALIFICATION_BORROW",
                FormBusinessType.QUALIFICATION_BORROW,
                List.of(
                        new FormFieldDefinition("qualificationId", "资质", FormFieldType.QUALIFICATION, true),
                        new FormFieldDefinition("purpose", "用途", FormFieldType.TEXTAREA, true)
                )
        );

        ValidationResult result = FormSchemaValidator.validate(schema);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "资质借阅表单缺少业务字段: borrower",
                "资质借阅表单缺少业务字段: department",
                "资质借阅表单缺少业务字段: projectId",
                "资质借阅表单缺少业务字段: expectedReturnDate"
        );
    }
}
