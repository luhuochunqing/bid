package com.xiyu.bid.workflowform.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OaResultApplicationPolicyTest {

    @Test
    void only_oa_approved_result_can_apply_business_effect() {
        assertThat(OaResultApplicationPolicy.canApplyBusiness(OaApprovalStatus.APPROVED)).isTrue();
        assertThat(OaResultApplicationPolicy.canApplyBusiness(OaApprovalStatus.REJECTED)).isFalse();
        assertThat(OaResultApplicationPolicy.canApplyBusiness(OaApprovalStatus.FAILED)).isFalse();
        assertThat(OaResultApplicationPolicy.canApplyBusiness(OaApprovalStatus.PROCESSING)).isFalse();
    }
}
