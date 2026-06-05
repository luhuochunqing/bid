package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFormSchemaPolicyTest {

    @Test
    void accepts_productized_field_types_for_admin_designed_forms() {
        Map<String, Object> schema = Map.of("fields", List.of(
                field("title", "申请标题", "text", true),
                field("useDate", "使用日期", "date", true),
                field("amount", "金额", "number", false),
                Map.of("key", "approver", "label", "审批人", "type", "person", "required", true),
                Map.of("key", "projectId", "label", "项目", "type", "project", "required", false),
                Map.of("key", "files", "label", "附件", "type", "attachment", "required", false),
                Map.of("key", "tips", "label", "说明", "type", "info", "content", "请按 OA 要求填写"),
                Map.of("key", "level", "label", "级别", "type", "select", "options", List.of(Map.of("label", "普通", "value", "normal")))
        ));

        ValidationResult result = WorkflowFormSchemaPolicy.validate(schema);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejects_duplicate_keys_unknown_types_and_select_without_options() {
        Map<String, Object> schema = Map.of("fields", List.of(
                field("title", "申请标题", "text", true),
                field("title", "重复标题", "text", false),
                field("script", "脚本", "script", false),
                field("level", "级别", "select", false)
        ));

        ValidationResult result = WorkflowFormSchemaPolicy.validate(schema);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "字段 key 不得重复: title",
                "字段类型不支持: script",
                "下拉字段必须配置选项: level"
        );
    }

    private static Map<String, Object> field(String key, String label, String type, boolean required) {
        return Map.of("key", key, "label", label, "type", type, "required", required);
    }
}
