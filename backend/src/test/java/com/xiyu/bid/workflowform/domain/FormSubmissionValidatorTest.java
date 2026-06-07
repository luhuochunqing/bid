package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class FormSubmissionValidatorTest {

    @Test
    void rejects_empty_required_qualification_borrow_values() {
        ValidationResult result = FormSubmissionValidator.validateQualificationBorrow(Map.of(
                "qualificationId", "",
                "borrower", "",
                "department", "投标管理部",
                "projectId", "",
                "purpose", "",
                "expectedReturnDate", ""
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "请选择资质",
                "请填写借用人",
                "请选择项目",
                "请填写用途",
                "请选择预计归还日期"
        );
    }

    @Test
    void accepts_complete_qualification_borrow_values() {
        ValidationResult result = FormSubmissionValidator.validateQualificationBorrow(Map.of(
                "qualificationId", "1001",
                "borrower", "小王",
                "department", "投标管理部",
                "projectId", "P-2026-001",
                "purpose", "用于投标文件编制",
                "expectedReturnDate", "2026-05-10"
        ));

        assertThat(result.valid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void accepts_attachment_field_values_as_list_of_maps_or_array() {
        FormSchema schema = schemaWithRequiredAttachment();

        ValidationResult listResult = FormSubmissionValidator.validate(schema, Map.of(
                "attachments", List.of(
                        Map.of(
                                "fileName", "授权书.pdf",
                                "storagePath", "workflow/2026/auth.pdf",
                                "contentType", "application/pdf",
                                "size", 1024L
                        )
                )
        ));
        ValidationResult arrayResult = FormSubmissionValidator.validate(schema, Map.of(
                "attachments", new Map[]{
                        Map.of(
                                "fileName", "报价单.xlsx",
                                "fileUrl", "https://files.example.com/quote.xlsx"
                        )
                }
        ));

        assertThat(listResult.valid()).isTrue();
        assertThat(listResult.errors()).isEmpty();
        assertThat(arrayResult.valid()).isTrue();
        assertThat(arrayResult.errors()).isEmpty();
    }

    @Test
    void rejects_required_attachment_field_without_files() {
        ValidationResult result = FormSubmissionValidator.validate(schemaWithRequiredAttachment(), Map.of(
                "attachments", List.of()
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains("请上传附件");
    }

    @Test
    void rejects_attachment_item_without_file_name_or_storage_location() {
        ValidationResult result = FormSubmissionValidator.validate(schemaWithRequiredAttachment(), Map.of(
                "attachments", List.of(Map.of("contentType", "application/pdf"))
        ));

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "附件第 1 项缺少文件名",
                "附件第 1 项缺少存储路径或文件地址"
        );
    }

    @Test
    void does_not_mutate_attachment_input_values() {
        Map<String, Object> attachment = Map.of(
                "fileName", "授权书.pdf",
                "storagePath", "workflow/2026/auth.pdf"
        );
        List<Map<String, Object>> attachments = List.of(attachment);

        ValidationResult result = FormSubmissionValidator.validate(schemaWithRequiredAttachment(), Map.of(
                "attachments", attachments
        ));

        assertThat(result.valid()).isTrue();
        assertThat(attachments).containsExactly(attachment);
    }

    private static FormSchema schemaWithRequiredAttachment() {
        return new FormSchema(
                "qualification-borrow",
                FormBusinessType.QUALIFICATION_BORROW,
                List.of(new FormFieldDefinition("attachments", "附件", FormFieldType.ATTACHMENT, true))
        );
    }
}
