package com.xiyu.bid.integration.organization.domain;

public enum OrganizationEventStatus {
    PROCESSING,
    PROCESSED,
    DUPLICATE,
    REJECTED,
    FAILED,
    PENDING_RETRY,
    DEAD_LETTER
}
