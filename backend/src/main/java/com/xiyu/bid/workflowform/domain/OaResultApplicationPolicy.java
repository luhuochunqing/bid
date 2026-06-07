package com.xiyu.bid.workflowform.domain;

public final class OaResultApplicationPolicy {

    private OaResultApplicationPolicy() {
    }

    public static boolean canApplyBusiness(OaApprovalStatus status) {
        return status == OaApprovalStatus.APPROVED;
    }
}
