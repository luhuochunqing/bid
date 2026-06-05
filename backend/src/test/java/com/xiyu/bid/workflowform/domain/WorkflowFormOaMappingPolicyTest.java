package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFormOaMappingPolicyTest {

    @Test
    void accepts_safe_weaver_field_mapping_sources() {
        Map<String, Object> mapping = Map.of(
                "workflowCode", "WF_APPLY",
                "mainFields", List.of(
                        Map.of("source", "formData.title", "target", "field001", "type", "string", "required", true),
                        Map.of("source", "context.formInstanceId", "target", "field002", "type", "string"),
                        Map.of("source", "applicant.name", "target", "field003", "type", "string")
                )
        );

        ValidationResult result = WorkflowFormOaMappingPolicy.validate(mapping);

        assertThat(result.valid()).isTrue();
    }

    @Test
    void rejects_missing_workflow_code_duplicate_targets_and_unsafe_sources() {
        Map<String, Object> mapping = Map.of(
                "mainFields", List.of(
                        Map.of("source", "formData.title", "target", "field001", "type", "string"),
                        Map.of("source", "System.getenv.SECRET", "target", "field002", "type", "string"),
                        Map.of("source", "context.formInstanceId", "target", "field001", "type", "string")
                )
        );

        ValidationResult result = WorkflowFormOaMappingPolicy.validate(mapping);

        assertThat(result.valid()).isFalse();
        assertThat(result.errors()).contains(
                "OA 流程编码不能为空",
                "OA 字段 target 不得重复: field001",
                "OA 映射 source 不在白名单内: System.getenv.SECRET"
        );
    }
}
