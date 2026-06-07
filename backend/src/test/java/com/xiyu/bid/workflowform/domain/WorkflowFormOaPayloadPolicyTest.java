package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class WorkflowFormOaPayloadPolicyTest {

    @Test
    void maps_form_data_to_trial_oa_payload_without_side_effects() {
        Map<String, Object> payload = WorkflowFormOaPayloadPolicy.buildPayload(
                mapping(),
                Map.of("title", "用章申请", "amount", 12),
                Map.of("formInstanceId", "PREVIEW"),
                Map.of("name", "李总"),
                true
        );

        assertThat(payload).containsEntry("workflowCode", "WF_SEAL");
        assertThat(payload).containsEntry("trial", true);
        assertThat((Map<String, Object>) payload.get("mainFields")).containsEntry("field_title", "用章申请")
                .containsEntry("field_amount", 12)
                .containsEntry("field_external_id", "PREVIEW")
                .containsEntry("field_applicant", "李总");
    }

    @Test
    void real_submit_payload_does_not_carry_trial_mode() {
        Map<String, Object> payload = WorkflowFormOaPayloadPolicy.buildPayload(
                mapping(),
                Map.of("title", "用章申请", "amount", 12),
                Map.of("formInstanceId", "100"),
                Map.of("name", "李总"),
                false
        );

        assertThat(payload).containsEntry("trial", false);
    }

    private static Map<String, Object> mapping() {
        return Map.of(
                "workflowCode", "WF_SEAL",
                "mainFields", List.of(
                        Map.of("source", "formData.title", "target", "field_title"),
                        Map.of("source", "formData.amount", "target", "field_amount"),
                        Map.of("source", "context.formInstanceId", "target", "field_external_id"),
                        Map.of("source", "applicant.name", "target", "field_applicant")
                )
        );
    }
}
