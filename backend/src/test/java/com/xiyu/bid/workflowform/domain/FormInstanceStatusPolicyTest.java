package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FormInstanceStatusPolicyTest {

    @Test
    void allows_expected_oa_submission_lifecycle() {
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.SUBMITTED, WorkflowFormStatus.OA_STARTING)).isTrue();
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.SUBMITTED, WorkflowFormStatus.OA_APPROVING)).isTrue();
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.OA_STARTING, WorkflowFormStatus.OA_APPROVING)).isTrue();
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.OA_APPROVING, WorkflowFormStatus.OA_APPROVED)).isTrue();
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.OA_APPROVED, WorkflowFormStatus.BUSINESS_APPLIED)).isTrue();
    }

    @Test
    void rejects_business_apply_before_oa_approved() {
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.OA_APPROVING, WorkflowFormStatus.BUSINESS_APPLIED)).isFalse();
        assertThat(FormInstanceStatusPolicy.canTransit(WorkflowFormStatus.OA_REJECTED, WorkflowFormStatus.BUSINESS_APPLIED)).isFalse();
    }
}
